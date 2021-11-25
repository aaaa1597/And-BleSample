package com.test.blesample.central;

import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.content.Context;
import android.content.Intent;
import android.icu.text.MessageFormat;
import android.os.Binder;
import android.os.Build;
import android.os.IBinder;
import android.support.annotation.RequiresApi;
import android.util.Log;
import java.util.List;

import static com.test.blesample.central.Constants.BODY_SENSOR_LOCATION_CHARACTERISTIC_UUID;

/**
 * Created by itanbarpeled on 28/01/2018.
 */

public class CentralService extends Service {

	private static final int STATE_DISCONNECTED	= 0;
	private static final int STATE_CONNECTING	= 1;
	private static final int STATE_CONNECTED	= 2;

	public final static String UWS_GATT_CONNECTED			= "UWS_GATT_CONNECTED";
	public final static String UWS_GATT_DISCONNECTED		= "UWS_GATT_DISCONNECTED";
	public final static String UWS_GATT_SERVICES_DISCOVERED	= "UWS_GATT_SERVICES_DISCOVERED";
	public final static String UWS_DATA_AVAILABLE			= "UWS_DATA_AVAILABLE";
	public final static String UWS_DATA						= "UWS_DATA";


	private BluetoothManager mBluetoothManager;
	private BluetoothAdapter mBluetoothAdapter;
	private String mBluetoothDeviceAddress;
	private BluetoothGatt mBluetoothGatt;

	private final IBinder mBinder = new LocalBinder();
	private int mConnectionState = STATE_DISCONNECTED;


	/*
	Implements callback methods for GATT events that the app cares about.  For example,
	connection change and services discovered.
	*/
	private final BluetoothGattCallback mGattCallback = new BluetoothGattCallback() {

		@Override
		public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
			TLog.d("BluetoothGattCallback::onConnectionStateChange() s");
			String intentAction;

			if (newState == BluetoothProfile.STATE_CONNECTED) {
				intentAction = UWS_GATT_CONNECTED;
				mConnectionState = STATE_CONNECTED;
				broadcastUpdate(intentAction);
				Log.i("aaaaa", "Connected to GATT server.");
				// Attempts to discover services after successful connection.
				Log.i("aaaaa", "Attempting to start service discovery:" + mBluetoothGatt.discoverServices());

			} else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
				intentAction = UWS_GATT_DISCONNECTED;
				mConnectionState = STATE_DISCONNECTED;
				Log.i("aaaaa", "Disconnected from GATT server.");
				broadcastUpdate(intentAction);
			}
			TLog.d("BluetoothGattCallback::onConnectionStateChange() e");
		}

		@Override
		public void onServicesDiscovered(BluetoothGatt gatt, int status) {
			TLog.d("BluetoothGattCallback::onServicesDiscovered() s");
			if (status == BluetoothGatt.GATT_SUCCESS) {
				broadcastUpdate(UWS_GATT_SERVICES_DISCOVERED);
			} else {
				Log.w("aaaaa", "onServicesDiscovered received: " + status);
			}
			TLog.d("BluetoothGattCallback::onServicesDiscovered() e");
		}

		@Override
		public void onCharacteristicRead(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
			TLog.d("BluetoothGattCallback::onCharacteristicRead() s");
			if (status == BluetoothGatt.GATT_SUCCESS) {
				broadcastUpdate(UWS_DATA_AVAILABLE, characteristic);
			} else {
				Log.w("aaaaa", "onCharacteristicRead GATT_FAILURE");
			}
			TLog.d("BluetoothGattCallback::onCharacteristicRead() e");
		}

		@Override
		public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
			TLog.d("BluetoothGattCallback::onCharacteristicChanged() s");
			broadcastUpdate(UWS_DATA_AVAILABLE, characteristic);
			TLog.d("BluetoothGattCallback::onCharacteristicChanged() e");
		}
	};


	private void broadcastUpdate(final String action) {
		TLog.d("broadcastUpdate() s");
		final Intent intent = new Intent(action);
		sendBroadcast(intent);
		TLog.d("broadcastUpdate() e");
	}


	private void broadcastUpdate(final String action, final BluetoothGattCharacteristic characteristic) {
		TLog.d("broadcastUpdate() 2 s");

		Intent intent = new Intent(action);

		/*
			This is special handling for the Heart Rate Measurement profile.  Data parsing is
			carried out as per profile specifications:
			http://developer.bluetooth.org/gatt/characteristics/Pages/CharacteristicViewer.aspx?u=org.bluetooth.characteristic.heart_rate_measurement.xml
		 */
		if (BODY_SENSOR_LOCATION_CHARACTERISTIC_UUID.equals(characteristic.getUuid())) {

			int flag = characteristic.getProperties();
			int format = -1;

			if ((flag & 0x01) != 0) {
				format = BluetoothGattCharacteristic.FORMAT_UINT16;
				Log.d("aaaaa", "data format UINT16.");
			} else {
				format = BluetoothGattCharacteristic.FORMAT_UINT8;
				Log.d("aaaaa", "data format UINT16.");
			}

			int msg = characteristic.getIntValue(format, 0);
			Log.d("aaaaa", String.format("message: %d", msg));
			intent.putExtra(UWS_DATA, msg);

		} else {

			/*
			for all other profiles, writes the data formatted in HEX.
			this code isn't relevant for this project.
			*/
			final byte[] data = characteristic.getValue();

			if (data != null && data.length > 0) {
				final StringBuilder stringBuilder = new StringBuilder(data.length);
				for (byte byteChar : data) {
					stringBuilder.append(String.format("%02X ", byteChar));
				}

				Log.w("aaaaa", "broadcastUpdate. general profile");
				intent.putExtra(UWS_DATA, -1);
				//intent.putExtra(EXTRA_DATA, new String(data) + "\n" + stringBuilder.toString());
			}
		}


		sendBroadcast(intent);
		TLog.d("broadcastUpdate() 2 e");
	}

	public class LocalBinder extends Binder {
		CentralService getService() {
			TLog.d("LocalBinder::getService() s-e");
			return CentralService.this;
		}
	}

	@Override
	public IBinder onBind(Intent intent) {
		TLog.d("onBind() s-e");
		return mBinder;
	}

	@Override
	public boolean onUnbind(Intent intent) {
		TLog.d("onUnbind() s");
		// After using a given device, you should make sure that BluetoothGatt.close() is called
		// such that resources are cleaned up properly.  In this particular example, close() is
		// invoked when the UI is disconnected from the Service.
		close();
		TLog.d("onUnbind() e");
		return super.onUnbind(intent);
	}


	/**
	 * Initializes a reference to the local Bluetooth adapter.
	 *
	 * @return Return true if the initialization is successful.
	 */
	public boolean initialize() {
		TLog.d("initialize() s");

		// For API level 18 and above, get a reference to BluetoothAdapter through BluetoothManager.
		if (mBluetoothManager == null) {

			mBluetoothManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);

			if (mBluetoothManager == null) {
				Log.e("aaaaa", "Unable to initialize BluetoothManager.");
				TLog.d("initialize() e false");
				return false;
			}
		}

		mBluetoothAdapter = mBluetoothManager.getAdapter();

		if (mBluetoothAdapter == null) {
			Log.e("aaaaa", "Unable to obtain a BluetoothAdapter.");
			TLog.d("initialize() e false");
			return false;
		}

		TLog.d("initialize() e");
		return true;
	}

	/**
	 * Connects to the GATT server hosted on the Bluetooth LE device.
	 *
	 * @param address The device address of the destination device.
	 *
	 * @return Return true if the connection is initiated successfully. The connection result
	 *         is reported asynchronously through the
	 *         {@code BluetoothGattCallback#onConnectionStateChange(android.bluetooth.BluetoothGatt, int, int)}
	 *         callback.
	 */
	public boolean connect(final String address) {
		TLog.d("connect() s");

		if (mBluetoothAdapter == null || address == null) {
			Log.w("aaaaa", "BluetoothAdapter not initialized or unspecified address.");
			TLog.d("connect() e false");
			return false;
		}

		// Previously connected device.  Try to reconnect.
		if (mBluetoothDeviceAddress != null && address.equals(mBluetoothDeviceAddress) && mBluetoothGatt != null) {
			Log.d("aaaaa", "Trying to use an existing mBluetoothGatt for connection.");
			if (mBluetoothGatt.connect()) {
				mConnectionState = STATE_CONNECTING;
				TLog.d("connect() e 249");
				return true;
			} else {
				TLog.d("connect() e false");
				return false;
			}
		}

		BluetoothDevice device = mBluetoothAdapter.getRemoteDevice(address);
		if (device == null) {
			Log.w("aaaaa", "Device not found.  Unable to connect.");
			TLog.d("connect() e false");
			return false;
		}

		// We want to directly connect to the device, so we are setting the autoConnect
		// parameter to false.
		mBluetoothGatt = device.connectGatt(this, false, mGattCallback);
		mBluetoothDeviceAddress = address;
		mConnectionState = STATE_CONNECTING;

		Log.d("aaaaa", "Trying to create a new connection.");

		TLog.d("connect() e");
		return true;
	}


	// TODO bluetooth - call this method when needed
	/**
	 * Disconnects an existing connection or cancel a pending connection. The disconnection result
	 * is reported asynchronously through the
	 * {@code BluetoothGattCallback#onConnectionStateChange(android.bluetooth.BluetoothGatt, int, int)}
	 * callback.
	 */
	public void disconnect() {
		TLog.d("disconnect() s");

		if (mBluetoothAdapter == null || mBluetoothGatt == null) {
			Log.w("aaaaa", "BluetoothAdapter not initialized");
			return;
		}

		mBluetoothGatt.disconnect();
		TLog.d("disconnect() e");
	}

	/**
	 * After using a given BLE device, the app must call this method to ensure resources are
	 * released properly.
	 */
	public void close() {
		TLog.d("close() s");

		if (mBluetoothGatt == null) {
			TLog.d("close() e");
			return;
		}

		mBluetoothGatt.close();
		mBluetoothGatt = null;
		TLog.d("close() e");
	}



	/**
	 * Request a read on a given {@code BluetoothGattCharacteristic}. The read result is reported
	 * asynchronously through the {@code BluetoothGattCallback#onCharacteristicRead(android.bluetooth.BluetoothGatt, android.bluetooth.BluetoothGattCharacteristic, int)}
	 * callback.
	 *
	 * @param characteristic The characteristic to read from.
	 */
	public void readCharacteristic(BluetoothGattCharacteristic characteristic) {
		TLog.d("readCharacteristic() s");

		if (mBluetoothAdapter == null || mBluetoothGatt == null) {
			Log.w("aaaaa", "BluetoothAdapter not initialized");
			TLog.d("readCharacteristic() e false");
			return;
		}

		mBluetoothGatt.readCharacteristic(characteristic);
		TLog.d("readCharacteristic() e");
	}

	/**
	 * Enables or disables notification on a give characteristic.
	 *
	 * @param characteristic Characteristic to act on.
	 * @param enabled If true, enable notification.  False otherwise.
	 */
	public void setCharacteristicNotification(BluetoothGattCharacteristic characteristic, boolean enabled) {
		TLog.d("setCharacteristicNotification() s");

		if (mBluetoothAdapter == null || mBluetoothGatt == null) {
			Log.w("aaaaa", "BluetoothAdapter not initialized");
			TLog.d("setCharacteristicNotification() e false");
			return;
		}

		mBluetoothGatt.setCharacteristicNotification(characteristic, enabled);

		// This is specific to Heart Rate Measurement.
		/*
		if (HEART_RATE_MEASUREMENT_UUID.toString().equals(characteristic.getUuid().toString())) {
			BluetoothGattDescriptor descriptor = characteristic.getDescriptor(CLIENT_CHARACTERISTIC_CONFIGURATION_UUID);
			descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
			mBluetoothGatt.writeDescriptor(descriptor);
		}
		*/

		TLog.d("setCharacteristicNotification() e");
	}

	/**
	 * Retrieves a list of supported GATT services on the connected device. This should be
	 * invoked only after {@code BluetoothGatt#discoverServices()} completes successfully.
	 *
	 * @return A {@code List} of supported services.
	 */
	public List<BluetoothGattService> getSupportedGattServices() {
		TLog.d("getSupportedGattServices() s");
		if (mBluetoothGatt == null) {
			return null;
		}

		TLog.d("getSupportedGattServices() e");
		return mBluetoothGatt.getServices();
	}

	public static class TLog {
		@RequiresApi(api = Build.VERSION_CODES.N)
		public static void d(String logstr) {
			StackTraceElement callaaa = new Throwable().getStackTrace()[2];
			String headaaa = MessageFormat.format("{0}::{1}({2}) ->", callaaa.getClassName(), callaaa.getMethodName(), callaaa.getLineNumber());
			Log.d("bbbbb", MessageFormat.format("{0}",headaaa));

			StackTraceElement throwableStackTraceElement = new Throwable().getStackTrace()[1];
			String head = MessageFormat.format("    {0}::{1}({2})", throwableStackTraceElement.getClassName(), throwableStackTraceElement.getMethodName(), throwableStackTraceElement.getLineNumber());
			Log.d("bbbbb", MessageFormat.format("{0} {1}",head, logstr));
		}

		@RequiresApi(api = Build.VERSION_CODES.N)
		public static void d(String fmt, Object... args) {
			StackTraceElement callaaa = new Throwable().getStackTrace()[2];
			String headaaa = MessageFormat.format("{0}::{1}({2}) ->", callaaa.getClassName(), callaaa.getMethodName(), callaaa.getLineNumber());
			Log.d("bbbbb", MessageFormat.format("{0}",headaaa));

			StackTraceElement throwableStackTraceElement = new Throwable().getStackTrace()[1];
			String head = MessageFormat.format("    {0}::{1}({2})", throwableStackTraceElement.getClassName(), throwableStackTraceElement.getMethodName(), throwableStackTraceElement.getLineNumber());
			String arglogstr =  MessageFormat.format(fmt, (Object[])args);
			Log.d("bbbbb", MessageFormat.format("{0} {1}",head, arglogstr));
		}

		@RequiresApi(api = Build.VERSION_CODES.N)
		public static void i(String logstr) {
			StackTraceElement callaaa = new Throwable().getStackTrace()[2];
			String headaaa = MessageFormat.format("{0}::{1}({2}) ->", callaaa.getClassName(), callaaa.getMethodName(), callaaa.getLineNumber());
			Log.d("bbbbb", MessageFormat.format("{0}",headaaa));

			StackTraceElement throwableStackTraceElement = new Throwable().getStackTrace()[1];
			String head = MessageFormat.format("    {0}::{1}({2})", throwableStackTraceElement.getClassName(), throwableStackTraceElement.getMethodName(), throwableStackTraceElement.getLineNumber());
			Log.i("bbbbb", MessageFormat.format("{0} {1}",head, logstr));
		}

		@RequiresApi(api = Build.VERSION_CODES.N)
		public static void i(String fmt, Object... args) {
			StackTraceElement callaaa = new Throwable().getStackTrace()[2];
			String headaaa = MessageFormat.format("{0}::{1}({2}) ->", callaaa.getClassName(), callaaa.getMethodName(), callaaa.getLineNumber());
			Log.d("bbbbb", MessageFormat.format("{0}",headaaa));

			StackTraceElement throwableStackTraceElement = new Throwable().getStackTrace()[1];
			String head = MessageFormat.format("    {0}::{1}({2})", throwableStackTraceElement.getClassName(), throwableStackTraceElement.getMethodName(), throwableStackTraceElement.getLineNumber());
			String arglogstr =  MessageFormat.format(fmt, (Object[])args);
			Log.i("bbbbb", MessageFormat.format("{0} {1}",head, arglogstr));
		}

		@RequiresApi(api = Build.VERSION_CODES.N)
		public static void w(String logstr) {
			StackTraceElement callaaa = new Throwable().getStackTrace()[2];
			String headaaa = MessageFormat.format("{0}::{1}({2}) ->", callaaa.getClassName(), callaaa.getMethodName(), callaaa.getLineNumber());
			Log.d("bbbbb", MessageFormat.format("{0}",headaaa));

			StackTraceElement throwableStackTraceElement = new Throwable().getStackTrace()[1];
			String head = MessageFormat.format("    {0}::{1}({2})", throwableStackTraceElement.getClassName(), throwableStackTraceElement.getMethodName(), throwableStackTraceElement.getLineNumber());
			Log.w("bbbbb", MessageFormat.format("{0} {1}",head, logstr));
		}

		@RequiresApi(api = Build.VERSION_CODES.N)
		public static void w(String fmt, Object... args) {
			StackTraceElement callaaa = new Throwable().getStackTrace()[2];
			String headaaa = MessageFormat.format("{0}::{1}({2}) ->", callaaa.getClassName(), callaaa.getMethodName(), callaaa.getLineNumber());
			Log.d("bbbbb", MessageFormat.format("{0}",headaaa));

			StackTraceElement throwableStackTraceElement = new Throwable().getStackTrace()[1];
			String head = MessageFormat.format("    {0}::{1}({2})", throwableStackTraceElement.getClassName(), throwableStackTraceElement.getMethodName(), throwableStackTraceElement.getLineNumber());
			String arglogstr =  MessageFormat.format(fmt, (Object[])args);
			Log.w("bbbbb", MessageFormat.format("{0} {1}",head, arglogstr));
		}

		@RequiresApi(api = Build.VERSION_CODES.N)
		public static void e(String logstr) {
			StackTraceElement callaaa = new Throwable().getStackTrace()[2];
			String headaaa = MessageFormat.format("{0}::{1}({2}) ->", callaaa.getClassName(), callaaa.getMethodName(), callaaa.getLineNumber());
			Log.d("bbbbb", MessageFormat.format("{0}",headaaa));

			StackTraceElement throwableStackTraceElement = new Throwable().getStackTrace()[1];
			String head = MessageFormat.format("    {0}::{1}({2})", throwableStackTraceElement.getClassName(), throwableStackTraceElement.getMethodName(), throwableStackTraceElement.getLineNumber());
			Log.e("bbbbb", MessageFormat.format("{0} {1}",head, logstr));
		}

		@RequiresApi(api = Build.VERSION_CODES.N)
		public static void e(String fmt, Object... args) {
			StackTraceElement callaaa = new Throwable().getStackTrace()[2];
			String headaaa = MessageFormat.format("{0}::{1}({2}) ->", callaaa.getClassName(), callaaa.getMethodName(), callaaa.getLineNumber());
			Log.d("bbbbb", MessageFormat.format("{0}",headaaa));

			StackTraceElement throwableStackTraceElement = new Throwable().getStackTrace()[1];
			String head = MessageFormat.format("    {0}::{1}({2})", throwableStackTraceElement.getClassName(), throwableStackTraceElement.getMethodName(), throwableStackTraceElement.getLineNumber());
			String arglogstr =  MessageFormat.format(fmt, (Object[])args);
			Log.e("bbbbb", MessageFormat.format("{0} {1}",head, arglogstr));
		}
	}
}
