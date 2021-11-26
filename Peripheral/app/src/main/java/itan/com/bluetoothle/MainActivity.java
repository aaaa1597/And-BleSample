package itan.com.bluetoothle;

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
import android.os.Bundle;
import android.widget.RadioGroup;
import android.widget.Switch;

import java.util.Arrays;
import java.util.HashSet;

import static itan.com.bluetoothle.Constants.BODY_SENSOR_LOCATION_CHARACTERISTIC_UUID;
import static itan.com.bluetoothle.Constants.HEART_RATE_SERVICE_UUID;
import static itan.com.bluetoothle.Constants.BLEMSG_1;
import static itan.com.bluetoothle.Constants.BLEMSG_2;

/**
 BLEシーケンス
 peripheral		central
 	|				|
 	|	advertise	|
 	|-------------->|
 	|-------------->|
 	|		・		|
 	|	scan		|
 	|<--------------|
 	|	connect		|
 	|<--------------|
 	|	notify		|
 	|-------------->|
 	|	receive		|
 	|<--------------|
 	|				|
 */
public class MainActivity extends BluetoothActivity {

	private BluetoothGattCharacteristic mSampleCharacteristic;

	private BluetoothGattServer mGattServer;
	private HashSet<BluetoothDevice> mBluetoothDevices;


	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

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

		setGattServer();
		setBluetoothService();
	}

	@Override
	protected int getLayoutId() {
		return R.layout.activity_main;
	}


	@Override
	protected int getTitleString() {
		return R.string.peripheral_screen;
	}


	/**
	 * Starts BLE Advertising by starting {@code PeripheralAdvertiseService}.
	 */
	private void startAdvertising() {
		// TODO bluetooth - maybe bindService? what happens when closing app?
		startService(getServiceIntent(this));
	}


	/**
	 * Stops BLE Advertising by stopping {@code PeripheralAdvertiseService}.
	 */
	private void stopAdvertising() {
		stopService(getServiceIntent(this));
		((Switch)findViewById(R.id.advertise_switch)).setChecked(false);
	}

	private void setGattServer() {

		mBluetoothDevices = new HashSet<>();
		BluetoothManager bluetoothManager = (BluetoothManager)getSystemService(Context.BLUETOOTH_SERVICE);
		if (bluetoothManager != null) {
			mGattServer = bluetoothManager.openGattServer(this, mGattServerCallback);
		} else {
			showMsgText(R.string.error_unknown);
		}
	}

	private void setBluetoothService() {

		// create the Service
		BluetoothGattService sampleService = new BluetoothGattService(HEART_RATE_SERVICE_UUID, BluetoothGattService.SERVICE_TYPE_PRIMARY);

		/*
		create the Characteristic.
		we need to grant to the Client permission to read (for when the user clicks the "Request Characteristic" button).
		no need for notify permission as this is an action the Server initiate.
		 */
		mSampleCharacteristic = new BluetoothGattCharacteristic(BODY_SENSOR_LOCATION_CHARACTERISTIC_UUID, BluetoothGattCharacteristic.PROPERTY_NOTIFY | BluetoothGattCharacteristic.PROPERTY_READ, BluetoothGattCharacteristic.PERMISSION_READ);
		setCharacteristic(); // set initial state

		// add the Characteristic to the Service
		sampleService.addCharacteristic(mSampleCharacteristic);

		// add the Service to the Server/Peripheral
		if (mGattServer != null) {
			mGattServer.addService(sampleService);
		}
	}


	private void setCharacteristic() {
		setCharacteristic(R.id.color_option_1);
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
		int value = checkedId == R.id.color_option_1 ? BLEMSG_1 : BLEMSG_2;
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
		 *
		 * @param device
		 * @param status	int: Status of the connect or disconnect operation. BluetoothGatt.GATT_SUCCESS if the operation succeeds.
		 * @param newState	BluetoothProfile.STATE_DISCONNECTED, BluetoothProfile#STATE_CONNECTED
		 */
		@Override
		public void onConnectionStateChange(BluetoothDevice device, final int status, int newState) {
			super.onConnectionStateChange(device, status, newState);

			String msg;
			if (status == BluetoothGatt.GATT_SUCCESS) {

				if (newState == BluetoothGatt.STATE_CONNECTED) {

					mBluetoothDevices.add(device);

					msg = "Connected to device: " + device.getAddress();
					TLog.d(msg);
					showMsgText(msg);

				} else if (newState == BluetoothGatt.STATE_DISCONNECTED) {

					mBluetoothDevices.remove(device);

					msg = "Disconnected from device";
					TLog.d(msg);
					showMsgText(msg);
				}

			}
			else {
				mBluetoothDevices.remove(device);

				msg = getString(R.string.status_error_when_connecting) + ": " + status;
				TLog.e(msg);
				showMsgText(msg);

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
