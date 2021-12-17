package com.tks.uwsserverunit00;

import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.os.RemoteException;
import android.text.TextUtils;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import com.google.android.material.snackbar.Snackbar;
import com.tks.uwsserverunit00.ui.DeviceListAdapter;

import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import static com.tks.uwsserverunit00.Constants.UWS_CHARACTERISTIC_HRATBEAT_UUID;
import static com.tks.uwsserverunit00.Constants.UWS_NG_DEVICE_NOTFOUND;
import static com.tks.uwsserverunit00.Constants.UWS_SERVICE_UUID;
import static com.tks.uwsserverunit00.Constants.BLEMSG_1;

public class DeviceConnectActivity extends AppCompatActivity {
	public static final String EXTRAS_DEVICE_NAME	= "com.tks.uws.DEVICE_NAME";
	public static final String EXTRAS_DEVICE_ADDRESS= "com.tks.uws.DEVICE_ADDRESS";

	private final ArrayList<ArrayList<BluetoothGattCharacteristic>> mDeviceServices = new ArrayList<>();
	private IBleServerService			mBleServiceIf;
	private BluetoothGattCharacteristic	mCharacteristic;
	private String						mDeviceAddress;

	/* BLE管理のService */
	private final ServiceConnection mCon = new ServiceConnection() {
		@Override
		public void onServiceConnected(ComponentName componentName, IBinder service) {
			TLog.d("BLE管理サービス接続-確立 service={0}", service);
			mBleServiceIf = IBleServerService.Stub.asInterface(service);

			try { mBleServiceIf.setCallback(mCb); }
			catch (RemoteException e) { e.printStackTrace(); }

			int ret = 0;
			try { ret = mBleServiceIf.connectBleDevice(mDeviceAddress);}
			catch (RemoteException e) { e.printStackTrace();}
			if( ret < 0) {
				TLog.d("BLE初期化/接続失敗!!");
				if(ret == UWS_NG_DEVICE_NOTFOUND)
					Snackbar.make(findViewById(R.id.root_view_device), "デバイスアドレスなし!!\n前画面で、別のデバイスを選択して下さい。", Snackbar.LENGTH_LONG).show();
			}
			else {
				TLog.d("BLE初期化/接続成功.");
			}
		}
		@Override
		public void onServiceDisconnected(ComponentName componentName) {
			mBleServiceIf = null;
		}
	};

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_device_connect);

		/* 読出し要求ボタン */
		findViewById(R.id.btnReqReadCharacteristic).setOnClickListener(view -> {
			if (mBleServiceIf != null && mCharacteristic != null) {
				try { mBleServiceIf.readCharacteristic(mCharacteristic); }
				catch (RemoteException e) { e.printStackTrace(); }
			}
			else {
				Snackbar.make(findViewById(R.id.root_view_device), "Unknown error.", Snackbar.LENGTH_LONG).show();
			}
		});

		/* MainActivityからの引継ぎデータ取得 */
		Intent intentfromMainActivity = getIntent();
		if(intentfromMainActivity == null) {
			ErrPopUp.create(DeviceConnectActivity.this).setErrMsg("画面切替え失敗。内部でエラーが発生しました。メーカに問い合わせて下さい。").Show(DeviceConnectActivity.this);
			finish();
		}

		/* デバイス名設定 */
		String deviceName = intentfromMainActivity.getStringExtra(EXTRAS_DEVICE_NAME);
		((TextView)findViewById(R.id.txtConnectedDeviceName)).setText(TextUtils.isEmpty(deviceName) ? "" : deviceName);

		/* デバイスaddress保持 */
		mDeviceAddress = intentfromMainActivity.getStringExtra(EXTRAS_DEVICE_ADDRESS);

		/* BLE管理サービス起動 */
		TLog.d("BLE管理サービス起動");
		Intent intent = new Intent(this, BleServerService.class);
		intent.putExtra(EXTRAS_DEVICE_ADDRESS, mDeviceAddress);
		bindService(intent, mCon, BIND_AUTO_CREATE);
	}

	@Override
	protected void onDestroy() {
		super.onDestroy();
		unbindService(mCon);
		mBleServiceIf = null;
	}

//	private final BroadcastReceiver mIntentListner = new BroadcastReceiver() {
//		@Override
//		public void onReceive(Context context, Intent intent) {
//			String action = intent.getAction();
//			if (action == null)
//				return;
//
//		}
//	};

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
		runOnUiThread(() -> {
			((ImageView)findViewById(R.id.imvCharacteristicValue)).setImageResource(msg==BLEMSG_1 ? R.drawable.num1 : R.drawable.num2);
			Snackbar.make(findViewById(R.id.root_view_device), "Characteristic value received: "+msg, Snackbar.LENGTH_LONG).show();
		});
	}

	/** *****************
	 * AIDLコールバック
	 * *****************/
	private IBleServerServiceCallback mCb = new IBleServerServiceCallback.Stub() {
		@Override public void notifyDeviceInfolist() throws RemoteException { throw new RemoteException("no implicated!!!!!!!!!");}
		@Override public void notifyDeviceInfo() throws RemoteException { throw new RemoteException("no implicated!!!!!!!!!");}
		@Override public void notifyScanEnd() throws RemoteException { throw new RemoteException("no implicated!!!!!!!!!");}

		@Override
		public void notifyGattConnected(String Address) throws RemoteException {
			/* Gatt接続完了 */
			runOnUiThread(() -> {
				((TextView)findViewById(R.id.txtConnectionStatus)).setText(R.string.connected);
				findViewById(R.id.btnReqReadCharacteristic).setEnabled(true);
			});

			TLog.d("Gatt接続OK!! -> Services探索中. Address={0}", Address);
//			runOnUiThread(() -> { mDeviceListAdapter.setStatus(Address, DeviceListAdapter.ConnectStatus.EXPLORING); });
		}

		@Override
		public void notifyGattDisConnected(String Address) throws RemoteException {
			/* Gatt切断 */
			runOnUiThread(() -> {
				((TextView)findViewById(R.id.txtConnectionStatus)).setText(R.string.disconnected);
				findViewById(R.id.btnReqReadCharacteristic).setEnabled(false);
			});

			String logstr = MessageFormat.format("Gatt接続断!! Address={0}", Address);
			TLog.d(logstr);
//			Snackbar.make(findViewById(R.id.root_view), logstr, Snackbar.LENGTH_LONG).show();
//			runOnUiThread(() -> { mDeviceListAdapter.setStatus(Address, DeviceListAdapter.ConnectStatus.NONE); });
		}

		@Override
		public void notifyServicesDiscovered(String Address, int status) throws RemoteException {
			try { mCharacteristic = findTerget(mBleServiceIf.getSupportedGattServices(), UWS_SERVICE_UUID, UWS_CHARACTERISTIC_HRATBEAT_UUID); }
			catch (RemoteException e) { e.printStackTrace(); }
			if (mCharacteristic == null)
				TLog.d("mCharacteristic is null. 対象外デバイス.");
			else {
				try {
					mBleServiceIf.readCharacteristic(mCharacteristic);
					mBleServiceIf.setCharacteristicNotification(mCharacteristic, true);
				}
				catch (RemoteException e) {e.printStackTrace();}
			}
//			if(status == Constants.UWS_NG_GATT_SUCCESS) {
//				TLog.d("Services発見. -> 対象Serviceかチェック ret={0}", status);
//				runOnUiThread(() -> { mDeviceListAdapter.setStatus(Address, DeviceListAdapter.ConnectStatus.CHECKAPPLI); });
//			}
//			else {
//				String logstr = MessageFormat.format("Services探索失敗!! 処理終了 ret={0}", status);
//				TLog.d(logstr);
//				runOnUiThread(() -> { mDeviceListAdapter.setStatus(Address, DeviceListAdapter.ConnectStatus.NONE); });
//				Snackbar.make(findViewById(R.id.root_view), logstr, Snackbar.LENGTH_LONG).show();
//			}
		}

		@Override
		public void notifyApplicable(String Address, boolean status) throws RemoteException {
			/* TODO */
//			if(status) {
//				TLog.d("対象Chk-OK. -> 通信準備中 Address={0}", Address);
//				int pos = mDeviceListAdapter.setStatus(Address, DeviceListAdapter.ConnectStatus.TOBEPREPARED);
//				mNotifyItemChanged.postValue(pos);
//			}
//			else {
//				String logstr = MessageFormat.format("対象外デバイス.　処理終了. Address={0}", Address);
//				TLog.d(logstr);
//				int pos = mDeviceListAdapter.setStatus(Address, DeviceListAdapter.ConnectStatus.NONE);
//				mNotifyItemChanged.postValue(pos);
////TODO			Snackbar.make(findViewById(R.id.root_view), logstr, Snackbar.LENGTH_LONG).show();
//			}
		}

		@Override
		public void notifyReady2DeviceCommunication(String Address, boolean status) throws RemoteException {
			/* TODO */
//			if(status) {
//				String logstr = MessageFormat.format("BLEデバイス通信 準備完了. Address={0}", Address);
//				TLog.d(logstr);
//				runOnUiThread(() -> { mDeviceListAdapter.setStatus(Address, DeviceListAdapter.ConnectStatus.READY); });
//				Snackbar.make(findViewById(R.id.root_view), logstr, Snackbar.LENGTH_LONG).show();
//			}
//			else {
//				String logstr = MessageFormat.format("BLEデバイス通信 準備失敗!! Address={0}", Address);
//				TLog.d(logstr);
//				runOnUiThread(() -> { mDeviceListAdapter.setStatus(Address, DeviceListAdapter.ConnectStatus.NONE); });
//				Snackbar.make(findViewById(R.id.root_view), logstr, Snackbar.LENGTH_LONG).show();
//			}
		}

		@Override
		public void notifyResRead(String Address, long ldatetime, double longitude, double latitude, int heartbeat, int status) throws RemoteException {
			/* TODO */
			TLog.d("RcvData(親->子)=" + heartbeat);
			rcvData(heartbeat);

			String logstr = MessageFormat.format("デバイス読込成功 {0}=({1} 経度:{2} 緯度:{3} 脈拍:{4}) status={5}", Address, new Date(ldatetime), longitude, latitude, heartbeat, status);
			TLog.d(logstr);
		}

		@Override
		public void notifyFromPeripheral(String Address, long ldatetime, double longitude, double latitude, int heartbeat) throws RemoteException {
			/* TODO */
			TLog.d("RcvData(親<-子)=" + heartbeat);
			rcvData(heartbeat);

			String logstr = MessageFormat.format("デバイス通知 {0}=({1} 経度:{2} 緯度:{3} 脈拍:{4})", Address, new Date(ldatetime), longitude, latitude, heartbeat);
			TLog.d(logstr);
		}

		@Override
		public void notifyError(int errcode, String errmsg) throws RemoteException {
			String logstr = MessageFormat.format("ERROR!! errcode={0} : {1}", errcode, errmsg);
			TLog.d(logstr);
//TODO			Snackbar.make(findViewById(R.id.root_view), logstr, Snackbar.LENGTH_LONG).show();
		}
	};
}
