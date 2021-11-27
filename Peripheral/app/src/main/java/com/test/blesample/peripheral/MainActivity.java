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

		/* Centralへ送信ボタン */
		findViewById(R.id.button_notify).setOnClickListener(view -> {
			notifyCharacteristicChanged();
		});
		/* アドバタイズ開始ボタン */
		findViewById(R.id.advertise_switch).setOnClickListener(view -> {
			if (((Switch)view).isChecked())
				startAdvertising();
			else
				stopAdvertising();
		});
		/* 値変更ボタン */
		((RadioGroup)findViewById(R.id.color_switch)).setOnCheckedChangeListener((group, checkedId) -> {
			setCharacteristic(checkedId);
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
			/* 自分自身のペリフェラル特性を定義 */
			setBluetoothService();
		}
	}

	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		super.onActivityResult(requestCode, resultCode, data);
		if(requestCode == REQUEST_ENABLE_BT) {
			/* Bluetooth機能ONになった。 */
			BluetoothManager bluetoothManager = (BluetoothManager)getSystemService(Context.BLUETOOTH_SERVICE);
			mGattServer = bluetoothManager.openGattServer(getApplicationContext(), mGattServerCallback);
			/* 自分自身のペリフェラル特性を定義 */
			setBluetoothService();
		}
		else {
			MsgPopUp.create(MainActivity.this).setErrMsg("Bluetooth機能をONにする必要があります。").Show(MainActivity.this);
		}
	}

	private void setBluetoothService() {
		/* 自身が提供するサービスを定義 */
		BluetoothGattService sampleService = new BluetoothGattService(UWS_SERVICE_UUID, BluetoothGattService.SERVICE_TYPE_PRIMARY);

		/*
		create the Characteristic.
		we need to grant to the Client permission to read (for when the user clicks the "Request Characteristic" button).
		no need for notify permission as this is an action the Server initiate.
		 */
		/* 自身が提供するCharacteristic(特性)を定義 :  */
		mSampleCharacteristic = new BluetoothGattCharacteristic(UWS_CHARACTERISTIC_SAMLE_UUID, BluetoothGattCharacteristic.PROPERTY_NOTIFY | BluetoothGattCharacteristic.PROPERTY_READ, BluetoothGattCharacteristic.PERMISSION_READ);
		setCharacteristic(); // set initial state

		// add the Characteristic to the Service
		sampleService.addCharacteristic(mSampleCharacteristic);

		// add the Service to the Server/Peripheral
		if (mGattServer != null) {
			mGattServer.addService(sampleService);
		}
	}

	/**
	 * Starts BLE Advertising by starting {@code PeripheralAdvertiseService}.
	 */
	private void startAdvertising() {
		// TODO bluetooth - maybe bindService? what happens when closing app?
		startService(getServiceIntent(getApplicationContext()));
	}


	/**
	 * Stops BLE Advertising by stopping {@code PeripheralAdvertiseService}.
	 */
	private void stopAdvertising() {
		stopService(getServiceIntent(getApplicationContext()));
		((Switch)findViewById(R.id.advertise_switch)).setChecked(false);
	}



	private void setCharacteristic() {
		setCharacteristic(R.id.rbn_value_1);
	}

	/*
	update the value of Characteristic.
	the client will receive the Characteristic value when:
		1. the Client user clicks the "Request Characteristic" button
		2. teh Server user clicks the "Notify Client" button

	value - can be between 0-255 according to:
	https://www.bluetooth.com/specifications/gatt/viewer?attributeXmlFile=org.bluetooth.characteristic.body_sensor_location.xml
	 */
	private void setCharacteristic(int checkedId) {
		/*
		done each time the user changes a value of a Characteristic
		 */
		int value = checkedId == R.id.rbn_value_1 ? BLEMSG_1 : BLEMSG_2;
		mSampleCharacteristic.setValue(getValue(value));
	}

	private byte[] getValue(int value) {
		return new byte[]{(byte) value};
	}

	/*
	send to the client the value of the Characteristic,
	as the user requested to notify.
	 */
	private void notifyCharacteristicChanged() {
		/*
		done when the user clicks the notify button in the app.
		indicate - true for indication (acknowledge) and false for notification (un-acknowledge).
		 */
		boolean indicate = (mSampleCharacteristic.getProperties() & BluetoothGattCharacteristic.PROPERTY_INDICATE) == BluetoothGattCharacteristic.PROPERTY_INDICATE;

		for (BluetoothDevice device : mBluetoothDevices) {
			if (mGattServer != null) {
				mGattServer.notifyCharacteristicChanged(device, mSampleCharacteristic, indicate);
			}
		}
	}

	/**
	 * Returns Intent addressed to the {@code PeripheralAdvertiseService} class.
	 */
	private Intent getServiceIntent(Context context) {
		return new Intent(context, PeripheralAdvertiseService.class);
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
			TLog.d("status-BluetoothGatt.GATT_SUCCESS({0}) newState-({1})", BluetoothGatt.GATT_SUCCESS, newState);

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


}
