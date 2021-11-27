package com.test.blesample.peripheral;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattServer;
import android.bluetooth.BluetoothGattServerCallback;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.widget.RadioGroup;
import android.widget.Switch;
import android.widget.Toast;
import java.util.Arrays;
import java.util.HashSet;

import static com.test.blesample.peripheral.Constants.UWS_SERVICE_UUID;
import static com.test.blesample.peripheral.Constants.UWS_CHARACTERISTIC_SAMLE_UUID;
import static com.test.blesample.peripheral.Constants.BLEMSG_1;
import static com.test.blesample.peripheral.Constants.BLEMSG_2;

public class MainActivity extends AppCompatActivity {
	private BluetoothGattCharacteristic		mSampleCharacteristic;
	private BluetoothGattServer				mGattServer;
	private final HashSet<BluetoothDevice>	mBluetoothDevices = new HashSet<>();
	private final static int  REQUEST_ENABLE_BT		= 0x1111;
	private final static int  REQUEST_PERMISSIONS	= 0x2222;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);

		/* 通知ボタン(Centralへ送信) */
		findViewById(R.id.button_notify).setOnClickListener(view -> {
			boolean indicate = (mSampleCharacteristic.getProperties() & BluetoothGattCharacteristic.PROPERTY_INDICATE) == BluetoothGattCharacteristic.PROPERTY_INDICATE;
			for (BluetoothDevice device : mBluetoothDevices) {
				mGattServer.notifyCharacteristicChanged(device, mSampleCharacteristic, indicate);
			}
		});

		/* アドバタイズ開始ボタン */
		findViewById(R.id.advertise_switch).setOnClickListener(view -> {
			if (((Switch)view).isChecked())
				/* アドバタイズ開始(サービス起動) */
				startService(new Intent(getApplicationContext(), PeripheralAdvertiseService.class));
			else {
				/* アドバタイズ停止(サービス終了) */
				stopService(new Intent(getApplicationContext(), PeripheralAdvertiseService.class));
			}
		});

		/* 値変更ボタン */
		((RadioGroup)findViewById(R.id.color_switch)).setOnCheckedChangeListener((group, checkedId) -> {
			int value = checkedId == R.id.rbn_value_1 ? BLEMSG_1 : BLEMSG_2;
			mSampleCharacteristic.setValue(int2bytes(value));
		});

		/* Bluetoothのサポート状況チェック 未サポート端末なら起動しない */
		if (!getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
			MsgPopUp.create(MainActivity.this).setErrMsg("Bluetoothが、未サポートの端末です。").Show(MainActivity.this);
		}

		/* 権限が許可されていない場合はリクエスト. */
		if(checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
			requestPermissions(new String[]{Manifest.permission.ACCESS_COARSE_LOCATION}, REQUEST_PERMISSIONS);
		}

		final BluetoothManager bluetoothManager = (BluetoothManager)getSystemService(Context.BLUETOOTH_SERVICE);
		BluetoothAdapter bluetoothAdapter = bluetoothManager.getAdapter();
		/* Bluetooth未サポート判定 未サポートならエラーpopupで終了 */
		if (bluetoothAdapter == null) {
			MsgPopUp.create(MainActivity.this).setErrMsg("Bluetoothが、未サポートの端末です。").Show(MainActivity.this);
		}
		/* Bluetooth ON/OFF判定 -> OFFならONにするようにリクエスト */
		else if( !bluetoothAdapter.isEnabled()) {
			Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
			startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
		}
		else {
			/* Bluetooth機能ONだった */
			mGattServer = bluetoothManager.openGattServer(this, mGattServerCallback);
			if(mGattServer == null) {
				MsgPopUp.create(MainActivity.this).setErrMsg("Ble初期化に失敗!!\n終了します。再起動で直る可能性があります。").Show(MainActivity.this);
				finish();
			}
			/* 自分自身のペリフェラル特性を定義 */
			defineOwnCharacteristic();
		}
	}

	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		super.onActivityResult(requestCode, resultCode, data);
		if(requestCode == REQUEST_ENABLE_BT) {
			/* Bluetooth機能ONになった。 */
			BluetoothManager bluetoothManager = (BluetoothManager)getSystemService(Context.BLUETOOTH_SERVICE);
			mGattServer = bluetoothManager.openGattServer(getApplicationContext(), mGattServerCallback);
			if(mGattServer == null) {
				MsgPopUp.create(MainActivity.this).setErrMsg("Ble初期化に失敗!!\n終了します。再起動で直る可能性があります。").Show(MainActivity.this);
				finish();
			}
			/* 自分自身のペリフェラル特性を定義 */
			defineOwnCharacteristic();
		}
		else {
			MsgPopUp.create(MainActivity.this).setErrMsg("Bluetooth機能をONにする必要があります。").Show(MainActivity.this);
		}
	}

	/* 自身のペリフェラル特性を定義 */
	private void defineOwnCharacteristic() {
		/* 自身が提供するサービスを定義 */
		BluetoothGattService ownService = new BluetoothGattService(UWS_SERVICE_UUID, BluetoothGattService.SERVICE_TYPE_PRIMARY);

		/* 自身が提供するCharacteristic(特性)を定義 : 通知と読込みに対し、読込み許可 */
		mSampleCharacteristic = new BluetoothGattCharacteristic(UWS_CHARACTERISTIC_SAMLE_UUID, BluetoothGattCharacteristic.PROPERTY_NOTIFY | BluetoothGattCharacteristic.PROPERTY_READ, BluetoothGattCharacteristic.PERMISSION_READ);
		mSampleCharacteristic.setValue(int2bytes(BLEMSG_1));

		/* 定義したサービスにCharacteristic(特性)を付与 */
		ownService.addCharacteristic(mSampleCharacteristic);

		/* 定義したサービスを有効化 */
		mGattServer.addService(ownService);
	}

	private final BluetoothGattServerCallback mGattServerCallback = new BluetoothGattServerCallback() {
		/**
		 * @param device
		 * @param status	int: Status of the connect or disconnect operation. BluetoothGatt.GATT_SUCCESS if the operation succeeds.
		 * @param newState	BluetoothProfile.STATE_DISCONNECTED, BluetoothProfile#STATE_CONNECTED
		 */
		@Override
		public void onConnectionStateChange(BluetoothDevice device, final int status, int newState) {
			super.onConnectionStateChange(device, status, newState);
			TLog.d("status={0} newState={1}", status, newState);
			TLog.d("status-BluetoothGatt.GATT_SUCCESS({0}) newState-BluetoothGatt.STATE_xxxx(STATE_CONNECTED({1}),STATE_DISCONNECTED({2}))", BluetoothGatt.GATT_SUCCESS, BluetoothGatt.STATE_CONNECTED, BluetoothGatt.STATE_DISCONNECTED);

			String msg;
			if (status == BluetoothGatt.GATT_SUCCESS) {
				if (newState == BluetoothGatt.STATE_CONNECTED) {
					mBluetoothDevices.add(device);
					TLog.d("Connected to device: {0}", device.getAddress());
				}
				else if (newState == BluetoothGatt.STATE_DISCONNECTED) {
					mBluetoothDevices.remove(device);

					msg = "Disconnected from device";
					TLog.d(msg);
					Toast.makeText(getApplicationContext(), msg, Toast.LENGTH_LONG).show();
				}

			}
			else {
				mBluetoothDevices.remove(device);

				msg = getString(R.string.status_error_when_connecting) + ": " + status;
				TLog.e(msg);
				Toast.makeText(getApplicationContext(), msg, Toast.LENGTH_LONG).show();

			}
		}


		@Override
		public void onNotificationSent(BluetoothDevice device, int status) {
			super.onNotificationSent(device, status);
			TLog.d("Notification sent. Status: " + status);
		}


		@Override
		public void onCharacteristicReadRequest(BluetoothDevice device, int requestId, int offset, BluetoothGattCharacteristic characteristic) {

			super.onCharacteristicReadRequest(device, requestId, offset, characteristic);

			if (mGattServer == null) {
				return;
			}

			TLog.d("Device tried to read characteristic: " + characteristic.getUuid());
			TLog.d("Value: " + Arrays.toString(characteristic.getValue()));

			mGattServer.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, characteristic.getValue());
		}


		@Override
		public void onCharacteristicWriteRequest(BluetoothDevice device, int requestId, BluetoothGattCharacteristic characteristic, boolean preparedWrite, boolean responseNeeded, int offset, byte[] value) {

			super.onCharacteristicWriteRequest(device, requestId, characteristic, preparedWrite, responseNeeded, offset, value);

			TLog.d("Characteristic Write request: " + Arrays.toString(value));

			mSampleCharacteristic.setValue(value);

			if (responseNeeded) {
				mGattServer.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, 0, value);
			}

		}

		@Override
		public void onDescriptorReadRequest(BluetoothDevice device, int requestId, int offset, BluetoothGattDescriptor descriptor) {

			super.onDescriptorReadRequest(device, requestId, offset, descriptor);

			if (mGattServer == null) {
				return;
			}

			TLog.d("Device tried to read descriptor: " + descriptor.getUuid());
			TLog.d("Value: " + Arrays.toString(descriptor.getValue()));

			mGattServer.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, descriptor.getValue());
		}

		@Override
		public void onDescriptorWriteRequest(BluetoothDevice device, int requestId,
											 BluetoothGattDescriptor descriptor, boolean preparedWrite, boolean responseNeeded,
											 int offset,
											 byte[] value) {

			super.onDescriptorWriteRequest(device, requestId, descriptor, preparedWrite, responseNeeded, offset, value);

			TLog.d("Descriptor Write Request " + descriptor.getUuid() + " " + Arrays.toString(value));

//            int status = BluetoothGatt.GATT_SUCCESS;
//            if (descriptor.getUuid() == CLIENT_CHARACTERISTIC_CONFIGURATION_UUID) {
//                BluetoothGattCharacteristic characteristic = descriptor.getCharacteristic();
//                boolean supportsNotifications = (characteristic.getProperties() &
//                        BluetoothGattCharacteristic.PROPERTY_NOTIFY) != 0;
//                boolean supportsIndications = (characteristic.getProperties() &
//                        BluetoothGattCharacteristic.PROPERTY_INDICATE) != 0;
//
//                if (!(supportsNotifications || supportsIndications)) {
//                    status = BluetoothGatt.GATT_REQUEST_NOT_SUPPORTED;
//                } else if (value.length != 2) {
//                    status = BluetoothGatt.GATT_INVALID_ATTRIBUTE_LENGTH;
//                } else if (Arrays.equals(value, BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE)) {
//                    status = BluetoothGatt.GATT_SUCCESS;
//                    mCurrentServiceFragment.notificationsDisabled(characteristic);
//                    descriptor.setValue(value);
//                } else if (supportsNotifications &&
//                        Arrays.equals(value, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)) {
//                    status = BluetoothGatt.GATT_SUCCESS;
//                    mCurrentServiceFragment.notificationsEnabled(characteristic, false /* indicate */);
//                    descriptor.setValue(value);
//                } else if (supportsIndications &&
//                        Arrays.equals(value, BluetoothGattDescriptor.ENABLE_INDICATION_VALUE)) {
//                    status = BluetoothGatt.GATT_SUCCESS;
//                    mCurrentServiceFragment.notificationsEnabled(characteristic, true /* indicate */);
//                    descriptor.setValue(value);
//                } else {
//                    status = BluetoothGatt.GATT_REQUEST_NOT_SUPPORTED;
//                }
//            } else {
//                status = BluetoothGatt.GATT_SUCCESS;
//                descriptor.setValue(value);
//            }
//            if (responseNeeded) {
//                mGattServer.sendResponse(device, requestId, status,
//            /* No need to respond with offset */ 0,
//            /* No need to respond with a value */ null);
//            }

		}
	};

	private byte[] int2bytes(int value) {
		return new byte[]{(byte) value};
	}
}
