package com.test.blesample.central;

import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.os.IBinder;
import android.os.Bundle;
import android.support.design.widget.Snackbar;
import android.support.v7.app.AppCompatActivity;
import android.text.TextUtils;
import android.util.Log;
import android.widget.ImageView;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import static com.test.blesample.central.Constants.BODY_SENSOR_LOCATION_CHARACTERISTIC_UUID;
import static com.test.blesample.central.Constants.HEART_RATE_SERVICE_UUID;
import static com.test.blesample.central.Constants.BLEMSG_1;

public class DeviceConnectActivity extends AppCompatActivity {
	public static final String EXTRAS_DEVICE_NAME	= "DEVICE_NAME";
	public static final String EXTRAS_DEVICE_ADDRESS= "DEVICE_ADDRESS";

	private final ArrayList<ArrayList<BluetoothGattCharacteristic>> mDeviceServices = new ArrayList<>();
	private BleMngService				mBLeMngServ;
	private BluetoothGattCharacteristic	mCharacteristic;
	private String						mDeviceAddress;

	/* BLE管理のService */
	private final ServiceConnection mCon = new ServiceConnection() {
		@Override
		public void onServiceConnected(ComponentName componentName, IBinder service) {
			TLog.d("BLE管理サービス接続-確立");
			mBLeMngServ = ((BleMngService.LocalBinder)service).getService();
			/* Bluetooth初期化 */
			boolean ret = mBLeMngServ.initBle();
			if( !ret) {
				Log.e("aaaaa", "initBLE Failed!!");
				ErrPopUp.create(DeviceConnectActivity.this).setErrMsg("Service起動に失敗!!終了します。").Show(DeviceConnectActivity.this);
			}
			/* Bluetooth接続 */
			boolean ret2 = mBLeMngServ.connect(mDeviceAddress);
			if( !ret2)
				Snackbar.make(findViewById(R.id.root_view_device), "デバイス接続失敗!!\n前画面で、別のデバイスを選択して下さい。", Snackbar.LENGTH_LONG).show();
		}
		@Override
		public void onServiceDisconnected(ComponentName componentName) {
			mBLeMngServ = null;
		}
	};

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_device_connect);

		/* 読出し要求ボタン */
		findViewById(R.id.btnReqReadCharacteristic).setOnClickListener(view -> {
			if (mBLeMngServ != null && mCharacteristic != null) {
				mBLeMngServ.readCharacteristic(mCharacteristic);
			}
			else {
				Snackbar.make(findViewById(R.id.root_view_device), "Unknown error.", Snackbar.LENGTH_LONG).show();
			}
		});

		/* BLE管理サービス起動 */
		TLog.d("BLE管理サービス起動");
		Intent intent = new Intent(this, BleMngService.class);
		bindService(intent, mCon, BIND_AUTO_CREATE);

		/* MainActivityからの引継ぎデータ取得 */
		Intent intentfromMainActivity = getIntent();
		if(intentfromMainActivity == null) return;

		/* デバイス名設定 */
		String deviceName = intentfromMainActivity.getStringExtra(EXTRAS_DEVICE_NAME);
		((TextView)findViewById(R.id.txtConnectedDeviceName)).setText(TextUtils.isEmpty(deviceName) ? "" : deviceName);

		/* デバイスaddress保持 */
		mDeviceAddress  = intentfromMainActivity.getStringExtra(EXTRAS_DEVICE_ADDRESS);
	}

	@Override
	protected void onResume() {
		super.onResume();
		/* 受信するブロードキャストintentを登録 */
		registerReceiver(mIntentListner, getIntentFilter());
	}

	private static IntentFilter getIntentFilter() {
		final IntentFilter intentFilter = new IntentFilter();
		intentFilter.addAction(BleMngService.UWS_GATT_CONNECTED);
		intentFilter.addAction(BleMngService.UWS_GATT_DISCONNECTED);
		intentFilter.addAction(BleMngService.UWS_GATT_SERVICES_DISCOVERED);
		intentFilter.addAction(BleMngService.UWS_DATA_AVAILABLE);
		return intentFilter;
	}

	@Override
	protected void onPause() {
		super.onPause();
		/* 設定したブロードキャストintentを解除 */
		unregisterReceiver(mIntentListner);
	}

	@Override
	protected void onDestroy() {
		super.onDestroy();
		unbindService(mCon);
		mBLeMngServ = null;
	}

	private final BroadcastReceiver mIntentListner = new BroadcastReceiver() {
		@Override
		public void onReceive(Context context, Intent intent) {
			String action = intent.getAction();
			if (action == null)
				return;

			switch (action) {
				/* Gattサーバ接続完了 */
				case BleMngService.UWS_GATT_CONNECTED:
					runOnUiThread(() -> {
						/* 表示 : Connected */
						((TextView)findViewById(R.id.txtConnectionStatus)).setText(R.string.connected);
					});
					findViewById(R.id.btnReqReadCharacteristic).setEnabled(true);
					break;

				/* Gattサーバ断 */
				case BleMngService.UWS_GATT_DISCONNECTED:
					runOnUiThread(() -> {
						/* 表示 : Disconnected */
						((TextView)findViewById(R.id.txtConnectionStatus)).setText(R.string.disconnected);
					});
					findViewById(R.id.btnReqReadCharacteristic).setEnabled(false);
					break;

				case BleMngService.UWS_GATT_SERVICES_DISCOVERED:
					mCharacteristic = findTerget(mBLeMngServ.getSupportedGattServices(), HEART_RATE_SERVICE_UUID, BODY_SENSOR_LOCATION_CHARACTERISTIC_UUID);
					if (mCharacteristic != null) {
						mBLeMngServ.readCharacteristic(mCharacteristic);
						mBLeMngServ.setCharacteristicNotification(mCharacteristic, true);
					}
					break;

				case BleMngService.UWS_DATA_AVAILABLE:
					int msg = intent.getIntExtra(BleMngService.UWS_DATA, -1);
					TLog.d("RcvData =" + msg);
					rcvData(msg);
					break;
			}
		}
	};

	private BluetoothGattCharacteristic findTerget(List<BluetoothGattService> supportedGattServices, UUID ServiceUuid, UUID CharacteristicUuid) {
		BluetoothGattCharacteristic ret = null;
		for (BluetoothGattService service : supportedGattServices) {
			if( !service.getUuid().equals(ServiceUuid))
				continue;

			final List<BluetoothGattCharacteristic> findc = service.getCharacteristics().stream().filter(c -> {
														return c.getUuid().equals(CharacteristicUuid);
													}).collect(Collectors.toList());
			/* 見つかった最後の分が有効なので、上書きする */
			ret = findc.get(0);
		}
		return ret;
	}

	private void rcvData(int msg) {
		((ImageView)findViewById(R.id.imvCharacteristicValue)).setImageResource(msg==BLEMSG_1 ? R.drawable.num1 : R.drawable.num2);
		Snackbar.make(findViewById(R.id.root_view_device), "Characteristic value received: "+msg, Snackbar.LENGTH_LONG).show();
	}
}
