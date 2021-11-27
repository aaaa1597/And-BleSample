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
import android.widget.ImageView;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import static com.test.blesample.central.Constants.UWS_CHARACTERISTIC_SAMLE_UUID;
import static com.test.blesample.central.Constants.UWS_SERVICE_UUID;
import static com.test.blesample.central.Constants.BLEMSG_1;

public class DeviceConnectActivity extends AppCompatActivity {
	public static final String EXTRAS_DEVICE_NAME	= "com.tks.uws.DEVICE_NAME";
	public static final String EXTRAS_DEVICE_ADDRESS= "com.tks.uws.DEVICE_ADDRESS";

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

		/* 受信するブロードキャストintentを登録 */
		final IntentFilter intentFilter = new IntentFilter();
		intentFilter.addAction(BleMngService.UWS_SERVICE_WAKEUP_OK);
		intentFilter.addAction(BleMngService.UWS_GATT_CONNECTED);
		intentFilter.addAction(BleMngService.UWS_GATT_DISCONNECTED);
		intentFilter.addAction(BleMngService.UWS_GATT_SERVICES_DISCOVERED);
		intentFilter.addAction(BleMngService.UWS_DATA_AVAILABLE);
		registerReceiver(mIntentListner, intentFilter);

		/* 読出し要求ボタン */
		findViewById(R.id.btnReqReadCharacteristic).setOnClickListener(view -> {
			if (mBLeMngServ != null && mCharacteristic != null) {
				mBLeMngServ.readCharacteristic(mCharacteristic);
			}
			else {
				Snackbar.make(findViewById(R.id.root_view_device), "Unknown error.", Snackbar.LENGTH_LONG).show();
			}
		});

		/* MainActivityからの引継ぎデータ取得 */
		Intent intentfromMainActivity = getIntent();
		if(intentfromMainActivity == null) {
			MsgPopUp.create(DeviceConnectActivity.this).setErrMsg("画面切替え失敗。内部でエラーが発生しました。メーカに問い合わせて下さい。").Show(DeviceConnectActivity.this);
			finish();
		}


		/* デバイス名設定 */
		String deviceName = intentfromMainActivity.getStringExtra(EXTRAS_DEVICE_NAME);
		((TextView)findViewById(R.id.txtConnectedDeviceName)).setText(TextUtils.isEmpty(deviceName) ? "" : deviceName);

		/* デバイスaddress保持 */
		mDeviceAddress = intentfromMainActivity.getStringExtra(EXTRAS_DEVICE_ADDRESS);

		/* BLE管理サービス起動 */
		TLog.d("BLE管理サービス起動");
		Intent intent = new Intent(this, BleMngService.class);
		intent.putExtra(EXTRAS_DEVICE_ADDRESS, mDeviceAddress);
		bindService(intent, mCon, BIND_AUTO_CREATE);
	}

	@Override
	protected void onDestroy() {
		super.onDestroy();
		unregisterReceiver(mIntentListner);	/* 設定したブロードキャストintentを解除 */
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
				/* Ble管理サービス正常起動 */
				case BleMngService.UWS_SERVICE_WAKEUP_OK:
					TLog.d("Service wake up OK.");
					break;

				/* Ble管理サービス起動失敗 */
				case BleMngService.UWS_SERVICE_WAKEUP_NG:
					int errReason = intent.getIntExtra(BleMngService.UWS_SERVICE_WAKEUP_NG_REASON, BleMngService.UWS_NG_REASON_INITBLE);
					if(errReason == BleMngService.UWS_NG_REASON_INITBLE)
						MsgPopUp.create(DeviceConnectActivity.this).setErrMsg("Service起動中のBT初期化に失敗!!終了します。").Show(DeviceConnectActivity.this);
					else if(errReason == BleMngService.UWS_NG_REASON_DEVICENOTFOUND)
						Snackbar.make(findViewById(R.id.root_view_device), "デバイスアドレスなし!!\n前画面で、別のデバイスを選択して下さい。", Snackbar.LENGTH_LONG).show();
					else if(errReason == BleMngService.UWS_NG_REASON_CONNECTBLE)
						Snackbar.make(findViewById(R.id.root_view_device), "デバイス接続失敗!!\n前画面で、別のデバイスを選択して下さい。", Snackbar.LENGTH_LONG).show();
					break;

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
					mCharacteristic = findTerget(mBLeMngServ.getSupportedGattServices(), UWS_SERVICE_UUID, UWS_CHARACTERISTIC_SAMLE_UUID);
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
			/*-*-----------------------------------*/
			for(BluetoothGattCharacteristic GattChara : service.getCharacteristics())
				TLog.d("{0} : service-UUID={1} Chara-UUID={2}", mDeviceAddress, service.getUuid(), GattChara.getUuid());
			/*-*-----------------------------------*/
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
