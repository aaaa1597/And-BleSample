package com.test.blesample.peripheral;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

import android.Manifest;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattServer;
import android.bluetooth.BluetoothGattServerCallback;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.le.AdvertiseCallback;
import android.bluetooth.le.AdvertiseData;
import android.bluetooth.le.AdvertiseSettings;
import android.bluetooth.le.BluetoothLeAdvertiser;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.icu.text.MessageFormat;
import android.os.Bundle;
import android.os.ParcelUuid;
import android.util.Log;

import java.util.UUID;

public class MainActivity extends AppCompatActivity {
	ActivityResultLauncher<Intent>	mStartForResult = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(),
			result -> {
				Log.d("aaaaa", "bt-OFF>ON() s");
				if (result.getResultCode() == Activity.RESULT_OK) {
					/* Bluetooth機能ONになった */
					Log.d("aaaaa", "Bluetooth OFF -> ON");
					startPeripheral();
				}
				else {
					ErrPopUp.create(MainActivity.this).setErrMsg("Bluetoothを有効にする必要があります。").Show(MainActivity.this);
				}
				Log.d("aaaaa", "bt-OFF>ON() e");
			});
	private final static int REQUEST_PERMISSIONS = 0x1111;

	private static final String UUID_SERVICE_STR  = "798f8c09-6549-4ae1-a5f4-4d8c402aba8b";
    private static final UUID   UUID_PSDI_SERVICE = UUID.fromString("704c0684-d459-4bce-8d54-b602cad5a80b");
    private static final UUID   UUID_PSDI         = UUID.fromString("2698726c-82a9-4b08-82b3-7d3b605a7a0e");
    private static final UUID   UUID_SERVICE      = UUID.fromString(UUID_SERVICE_STR);
    private static final UUID   UUID_WRITE        = UUID.fromString("c4467a2a-b099-4ab6-8ded-6478b11ae3d4");
    private static final UUID   UUID_READ         = UUID.fromString("6e11b73a-4f08-4c0c-a286-4ccf3ba3c02b");
    private static final UUID   UUID_NOTIFY       = UUID.fromString("ee78af1d-fa84-4db9-9c33-de34a20334dc");
    private static final UUID   UUID_DESC         = UUID.fromString("5d87dd13-5e34-4931-8827-662353dab4f7");

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);

		Log.d("aaaaa","aaaaa aaaaaaaaaaaaaa=");

		/* Bluetoothのサポート状況チェック 未サポート端末なら起動しない */
		if (!getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
			ErrPopUp.create(MainActivity.this).setErrMsg("Bluetoothが、未サポートの端末です。").Show(MainActivity.this);
		}

		/* 権限が許可されていない場合はリクエスト. */
		if(checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
			Log.d("aaaaa", "requestPermissions s");
			requestPermissions(new String[]{Manifest.permission.ACCESS_COARSE_LOCATION}, REQUEST_PERMISSIONS);
			Log.d("aaaaa", "requestPermissions e");
		}

		/* Bluetooth ON/OFF判定 -> OFFならONにするようにリクエスト */
		mBleManager = (BluetoothManager)getSystemService(Context.BLUETOOTH_SERVICE);
		mBluetoothAdapter = mBleManager.getAdapter();
		if (mBluetoothAdapter == null) {
			ErrPopUp.create(MainActivity.this).setErrMsg("Bluetoothが、未サポートの端末です。").Show(MainActivity.this);
//			return;	上の関数で終了するので、ここにはこない。
		}
		else if( !mBluetoothAdapter.isEnabled()) {
			Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
			mStartForResult.launch(enableBtIntent);
		}
		else {
			/* Bluetooth機能ONだった */
			startPeripheral();
		}
	}

	@Override
	public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
		Log.d("aaaaa", "onRequestPermissionsResult s");
		/* 権限リクエストの結果を取得する. */
		if (requestCode == REQUEST_PERMISSIONS) {
			if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
				/* Bluetooth使用の権限を得た */
				startPeripheral();
			} else {
				ErrPopUp.create(MainActivity.this).setErrMsg("失敗しました。\n\"許可\"を押下して、このアプリにBluetoothの権限を与えて下さい。\n終了します。").Show(MainActivity.this);
			}
		}
		/* 知らん応答なのでスルー。 */
		else {
			super.onRequestPermissionsResult(requestCode, permissions, grantResults);
		}
		Log.d("aaaaa", "onRequestPermissionsResult e");
	}

	private BluetoothManager			mBleManager;
	private BluetoothAdapter			mBluetoothAdapter;
	private BluetoothGattCharacteristic	mNotifyCharacteristic;
	private BluetoothGattServer			mBtGattServer;

	/* ペリフェラルとして起動 */
	private void startPeripheral() {
		Log.d("aaaaa", "startPrepare() *******************");
		/* Bluetooth機能がONになってないのでreturn */
		if( !mBluetoothAdapter.isEnabled()) {
			Log.d("aaaaa", "startPrepare() e Bluetooth機能がOFFってる。");
			return;
		}
		Log.d("aaaaa", "Bluetooth ON.");

		/* Bluetooth使用の権限がないのでreturn */
		if(checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
			Log.d("aaaaa", "startPrepare() e Bluetooth使用の権限が拒否られた。");
			return;
		}
		Log.d("aaaaa", "Bluetooth使用権限OK.");

		/* Bluetoothサポート有,Bluetooth使用権限有,Bluetooth ONなので、ペリフェラルとして起動 */
		BluetoothLeAdvertiser bLeAdvertiser = mBluetoothAdapter.getBluetoothLeAdvertiser();
		if(bLeAdvertiser == null)
			ErrPopUp.create(MainActivity.this).setErrMsg("ペリフェラル起動に失敗!!\nこの端末は、ペリフェラルに対応してません。\n終了します。").Show(MainActivity.this);

		runOnUiThread(new Runnable() {
			@Override
			public void run() {
				mBtGattServer = mBleManager.openGattServer(MainActivity.this, mGattServerCallback);
			}
		});

		while(mBtGattServer==null);

		BluetoothGattService btPsdiService = new BluetoothGattService(UUID_PSDI_SERVICE, BluetoothGattService.SERVICE_TYPE_PRIMARY);
		BluetoothGattCharacteristic lPsdiCharacteristic = new BluetoothGattCharacteristic(UUID_PSDI, BluetoothGattCharacteristic.PROPERTY_READ, BluetoothGattCharacteristic.PERMISSION_READ);
		btPsdiService.addCharacteristic(lPsdiCharacteristic);
		mBtGattServer.addService(btPsdiService);

		try { Thread.sleep(200); }catch(Exception ex){}

		BluetoothGattService btGattService = new BluetoothGattService(UUID_SERVICE, BluetoothGattService.SERVICE_TYPE_PRIMARY);

		BluetoothGattCharacteristic lBtCharacteristic1 = new BluetoothGattCharacteristic(UUID_WRITE, BluetoothGattCharacteristic.PROPERTY_WRITE, BluetoothGattCharacteristic.PERMISSION_WRITE);
		btGattService.addCharacteristic(lBtCharacteristic1);
		BluetoothGattCharacteristic lBtCharacteristic2 = new BluetoothGattCharacteristic(UUID_READ, BluetoothGattCharacteristic.PROPERTY_READ, BluetoothGattCharacteristic.PERMISSION_READ);
		btGattService.addCharacteristic(lBtCharacteristic2);
		mNotifyCharacteristic = new BluetoothGattCharacteristic(UUID_NOTIFY, BluetoothGattCharacteristic.PROPERTY_NOTIFY, BluetoothGattCharacteristic.PERMISSION_READ);
		btGattService.addCharacteristic(mNotifyCharacteristic);
		BluetoothGattDescriptor dataDescriptor = new BluetoothGattDescriptor(UUID_DESC, BluetoothGattDescriptor.PERMISSION_WRITE | BluetoothGattDescriptor.PERMISSION_READ);
		mNotifyCharacteristic.addDescriptor(dataDescriptor);
		mBtGattServer.addService(btGattService);

		try { Thread.sleep(200); }catch(Exception ex){}

		startBleAdvertising(bLeAdvertiser);
	}

	private void startBleAdvertising(BluetoothLeAdvertiser bLeAdvertiser){
		AdvertiseData.Builder dataBuilder = new AdvertiseData.Builder();
		dataBuilder.setIncludeTxPowerLevel(true);
		dataBuilder.addServiceUuid(ParcelUuid.fromString(UUID_SERVICE_STR));

		AdvertiseSettings.Builder settingsBuilder = new AdvertiseSettings.Builder();
		settingsBuilder.setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_BALANCED);
		settingsBuilder.setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_MEDIUM);
		settingsBuilder.setTimeout(0);
		settingsBuilder.setConnectable(true);

		AdvertiseData.Builder respBuilder = new AdvertiseData.Builder();
		respBuilder.setIncludeDeviceName(true);

		bLeAdvertiser.startAdvertising(settingsBuilder.build(), dataBuilder.build(), respBuilder.build(), new AdvertiseCallback(){
			@Override
			public void onStartSuccess(AdvertiseSettings settingsInEffect) {
				Log.d("aaaaa", "onStartSuccess");
			}
			@Override
			public void onStartFailure(int errorCode) {
				Log.d("aaaaa", "onStartFailure");
				Log.d("aaaaa", "BLEを開始できませんでした。");
			}
		});
	}

	/* Bluetoothイベントハンドラ */
	private BluetoothGattServerCallback mGattServerCallback = new BluetoothGattServerCallback() {
		private byte[] psdiValue		= new byte[8];
		private byte[] notifyDescValue	= new byte[2];
		private byte[] charValue		= new byte[500]; /* max 512 */
		private BluetoothDevice mConnectedDevice;

		/* パケットサイズ変更 */
		@Override public void onMtuChanged(BluetoothDevice device, int mtu){ Log.d("aaaaa", "onMtuChanged(" + mtu + ")"); }

		/* 状態遷移 */
		@Override
		public void onConnectionStateChange(android.bluetooth.BluetoothDevice device, int status, int newState) {
			Log.d("aaaaa", "onConnectionStateChange");

			if(newState == BluetoothProfile.STATE_CONNECTED){
				mConnectedDevice = device;
				Log.d("aaaaa", MessageFormat.format("STATE_CONNECTED:{0}", device.toString()));
				Log.d("aaaaa", MessageFormat.format("接続されました。address={0}", device.getAddress()));
			}
			else{
				Log.d("aaaaa", MessageFormat.format("Unknown STATE:{0}->{1}", status, newState));
				Log.d("aaaaa", "切断されました。");
			}
		}

		/* セントラルからのRead要求受信 */
		public void onCharacteristicReadRequest(android.bluetooth.BluetoothDevice device, int requestId, int offset, BluetoothGattCharacteristic characteristic) {
			Log.d("aaaaa", "onCharacteristicReadRequest");

			if( characteristic.getUuid().compareTo(UUID_PSDI) == 0) {
				Log.d("aaaaa", MessageFormat.format("RCV:(UUID(PSDI):{0}, reqid:{1},offset:{2})", UUID_PSDI,requestId,offset));
				Log.d("aaaaa", MessageFormat.format("SND:(reqid:{0},GATT_SUCCESS:{1},offset:{2},val:{3})", requestId, BluetoothGatt.GATT_SUCCESS, offset, psdiValue));
				mBtGattServer.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, psdiValue);
			}
			else if( characteristic.getUuid().compareTo(UUID_READ) == 0) {
				Log.d("aaaaa", MessageFormat.format("RCV:(UUID(READ):{0}, reqid:{1},offset:{2})", UUID_READ,requestId,offset));

				if( offset > charValue.length ) {
					Log.d("aaaaa", MessageFormat.format("SND:(reqid:{0},GATT_FAILURE:{1},offset:{2},val:{3})", requestId, BluetoothGatt.GATT_FAILURE, offset, null));
					mBtGattServer.sendResponse(device, requestId, BluetoothGatt.GATT_FAILURE, offset, null);
				}
				else {
					byte[] value = new byte[charValue.length - offset];
					System.arraycopy(charValue, offset, value, 0, value.length);
					Log.d("aaaaa", MessageFormat.format("SND:(reqid:{0},GATT_SUCCESS:{1},offset:{2},val:{3})", requestId, BluetoothGatt.GATT_SUCCESS, offset, value));
					mBtGattServer.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, value);
				}
			}
			else{
				Log.d("aaaaa", MessageFormat.format("SND:(reqid:{0},GATT_FAILURE:{1},offset:{2},val:{3})", requestId, BluetoothGatt.GATT_FAILURE, offset, null));
				mBtGattServer.sendResponse(device, requestId, BluetoothGatt.GATT_FAILURE, offset, null );
			}
		}

		/* セントラルからのWrite要求受信 */
		public void onCharacteristicWriteRequest(android.bluetooth.BluetoothDevice device, int requestId, BluetoothGattCharacteristic characteristic, boolean preparedWrite, boolean responseNeeded, int offset, byte[] value) {
			Log.d("aaaaa", "onCharacteristicWriteRequest");

			if( characteristic.getUuid().compareTo(UUID_WRITE) == 0 ) {
				Log.d("aaaaa", MessageFormat.format("RCV:(UUID(WRITE):{0}, reqid:{1},preparedWrite:{2},responseNeeded:{3},offset:{4},val={5})", UUID_WRITE, requestId, preparedWrite, responseNeeded, offset, value));
				if(offset < charValue.length ) {
					int len = value.length;
					if( (offset + len ) > charValue.length)
						len = charValue.length - offset;
					System.arraycopy(value, 0, charValue, offset, len);
					Log.d("aaaaa", MessageFormat.format("SND:(reqid:{0},GATT_SUCCESS:{1},offset:{2},val:{3})", requestId, BluetoothGatt.GATT_SUCCESS, offset, null));
					mBtGattServer.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, null);
				}
				else {
					Log.d("aaaaa", MessageFormat.format("SND:(reqid:{0},GATT_FAILURE:{1},offset:{2},val:{3})", requestId, BluetoothGatt.GATT_FAILURE, offset, null));
					mBtGattServer.sendResponse(device, requestId, BluetoothGatt.GATT_FAILURE, offset, null);
				}

				if( (notifyDescValue[0] & 0x01) != 0x00 ) {
					if (offset == 0 && value[0] == (byte) 0xff) {
						mNotifyCharacteristic.setValue(charValue);
						Log.d("aaaaa", "NTFY:()");
						mBtGattServer.notifyCharacteristicChanged(mConnectedDevice, mNotifyCharacteristic, false);
						Log.d("aaaaa", "Notificationしました。");
					}
				}
			}
			else{
				Log.d("aaaaa", MessageFormat.format("SND:(reqid:{0},GATT_FAILURE:{1},offset:{2},val:{3})", requestId, BluetoothGatt.GATT_FAILURE, offset, null));
				mBtGattServer.sendResponse(device, requestId, BluetoothGatt.GATT_FAILURE, offset, null);
			}
		}

		public void onDescriptorReadRequest(BluetoothDevice device, int requestId, int offset, BluetoothGattDescriptor descriptor) {
			Log.d("aaaaa", "onDescriptorReadRequest");

			if( descriptor.getUuid().compareTo(UUID_DESC) == 0 ) {
				Log.d("aaaaa", MessageFormat.format("RCV:(UUID(DESC):{0}, reqid:{1},offset:{2})", UUID_DESC, requestId, offset));
				Log.d("aaaaa", MessageFormat.format("SND:(reqid:{0},GATT_SUCCESS:{1},offset:{2},val:{3})", requestId, BluetoothGatt.GATT_SUCCESS, offset, notifyDescValue));
				mBtGattServer.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, notifyDescValue);
			}
		}

		public void onDescriptorWriteRequest(BluetoothDevice device, int requestId, BluetoothGattDescriptor descriptor, boolean preparedWrite, boolean responseNeeded, int offset, byte[] value) {
			Log.d("aaaaa", "onDescriptorWriteRequest");

			if( descriptor.getUuid().compareTo(UUID_DESC) == 0 ) {
				Log.d("aaaaa", MessageFormat.format("RCV:(UUID(DESC):{0},reqid:{1},preparedWrite:{2},responseNeeded:{3},offset:{4},value:{4})", UUID_DESC, requestId, preparedWrite, responseNeeded, offset, value));
				notifyDescValue[0] = value[0];
				notifyDescValue[1] = value[1];

				Log.d("aaaaa", MessageFormat.format("SND:(reqid:{0},GATT_SUCCESS:{1},offset:{2},val:{3})", requestId, BluetoothGatt.GATT_SUCCESS, offset, null));
				mBtGattServer.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, null);
			}
		}
	};
}