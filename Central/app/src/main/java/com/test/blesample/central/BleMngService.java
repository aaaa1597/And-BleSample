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

public class BleMngService extends Service {
	/* サブクラス : BleMngService.LocalBinder */
	public class LocalBinder extends Binder {
		BleMngService getService() {
			return BleMngService.this;
		}
	}
	/* 接続状態 */
	private static final int STATE_DISCONNECTED	= 0;
	private static final int STATE_CONNECTING	= 1;
	private static final int STATE_CONNECTED	= 2;
	/* メッセージID */
	public final static String UWS_GATT_CONNECTED			= "com.tks.uws.GATT_CONNECTED";
	public final static String UWS_GATT_DISCONNECTED		= "com.tks.uws.GATT_DISCONNECTED";
	public final static String UWS_GATT_SERVICES_DISCOVERED	= "com.tks.uws.GATT_SERVICES_DISCOVERED";
	public final static String UWS_DATA_AVAILABLE			= "com.tks.uws.DATA_AVAILABLE";
	public final static String UWS_DATA						= "com.tks.uws.DATA";
	/* Serviceのお約束 */
	private final IBinder mBinder = new LocalBinder();
//	private int mConnectionState = STATE_DISCONNECTED;
	/* Bt通信メンバ */
	private BluetoothManager	mBtManager;
	private BluetoothAdapter	mBtAdapter;
	private BluetoothGatt		mBleGatt;
	private String				mBleDeviceAddr;
	private final BluetoothGattCallback mGattCallback = new BluetoothGattCallback() {
		@Override
		public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
			TLog.d("BluetoothGattCallback::onConnectionStateChange() {0} -> {1}", status, newState);
			TLog.d("BluetoothProfile.STATE_CONNECTING({0}) STATE_CONNECTED({1}) STATE_DISCONNECTING({2}) STATE_DISCONNECTED({3})", BluetoothProfile.STATE_CONNECTING, BluetoothProfile.STATE_CONNECTED, BluetoothProfile.STATE_DISCONNECTING, BluetoothProfile.STATE_DISCONNECTED);
			/* Gattサーバ接続完了 */
			if(newState == BluetoothProfile.STATE_CONNECTED) {
//				mConnectionState = STATE_CONNECTED;
				/* Intent送信(Service->Activity) */
				sendBroadcast(new Intent(UWS_GATT_CONNECTED));
				TLog.d("GATTサーバ接続OK.");
				mBleGatt.discoverServices();
				TLog.i("Discovery開始");
			}
			/* Gattサーバ断 */
			else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
//				mConnectionState = STATE_DISCONNECTED;
				TLog.d("GATTサーバ断.");
				/* Intent送信(Service->Activity) */
				sendBroadcast(new Intent(UWS_GATT_DISCONNECTED));
			}
		}

		@Override
		public void onServicesDiscovered(BluetoothGatt gatt, int status) {
			TLog.w("Discovery終了 result : {0}", status);
			if (status == BluetoothGatt.GATT_SUCCESS) {
				/* Intent送信(Service->Activity) */
				sendBroadcast(new Intent(UWS_GATT_SERVICES_DISCOVERED));
			}
		}

		/* 読込み要求の応答 */
		@Override
		public void onCharacteristicRead(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
			TLog.d("読込み要求の応答 status=", status);
			if (status == BluetoothGatt.GATT_SUCCESS) {
				broadcastUpdate(UWS_DATA_AVAILABLE, characteristic);
			}
			else {
				Log.w("aaaaa", "onCharacteristicRead GATT_FAILURE");
			}
			TLog.d("BluetoothGattCallback::onCharacteristicRead() e");
		}

		@Override
		public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
			TLog.d("onCharacteristicChanged...こいつは何だ?");
			broadcastUpdate(UWS_DATA_AVAILABLE, characteristic);
		}
	};

	@Override
	public IBinder onBind(Intent intent) {
		TLog.d("onBind() s-e");
		return mBinder;
	}

	@Override
	public boolean onUnbind(Intent intent) {
		close();
		return super.onUnbind(intent);
	}

	public boolean initBle() {
		mBtManager = (BluetoothManager)getSystemService(Context.BLUETOOTH_SERVICE);
		if(mBtManager == null) return false;

		mBtAdapter = mBtManager.getAdapter();
		if(mBtAdapter == null) return false;

		return true;
	}

	public boolean connect(final String address) {
		if (mBtAdapter == null || address == null) {
			TLog.d("initBle()を呼び出す前に、この関数を呼び出した。");
			throw new IllegalStateException("initBle()を呼び出す前に、この関数を呼び出した。");
		}

		/* 再接続処理 */
		if (mBleDeviceAddr != null && address.equals(mBleDeviceAddr) && mBleGatt != null) {
			TLog.d("接続済のデバイスに再接続します。");
			if (mBleGatt.connect()) {
//				mConnectionState = STATE_CONNECTING;
				TLog.d("接続しました。");
				return true;
			}
			else {
				TLog.d("接続失敗。");
				return false;
			}
		}

		/* 初回接続 */
		BluetoothDevice device = mBtAdapter.getRemoteDevice(address);
		if (device == null) {
			TLog.d("デバイス({0})が見つかりません。接続できませんでした。", address);
			return false;
		}

		// We want to directly connect to the device, so we are setting the autoConnect parameter to false.
		mBleGatt = device.connectGatt(this, false, mGattCallback);
		mBleDeviceAddr = address;
//		mConnectionState = STATE_CONNECTING;
		TLog.d("GATTサーバ接続開始.address={0}", address);

		return true;
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

		}
		else {

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



	// TODO bluetooth - call this method when needed
	/**
	 * Disconnects an existing connection or cancel a pending connection. The disconnection result
	 * is reported asynchronously through the
	 * {@code BluetoothGattCallback#onConnectionStateChange(android.bluetooth.BluetoothGatt, int, int)}
	 * callback.
	 */
	public void disconnect() {
		TLog.d("disconnect() s");

		if (mBtAdapter == null || mBleGatt == null) {
			Log.w("aaaaa", "BluetoothAdapter not initialized");
			return;
		}

		mBleGatt.disconnect();
		TLog.d("disconnect() e");
	}

	public void close() {
		if (mBleGatt == null)
			return;
		mBleGatt.close();
		mBleGatt = null;
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

		if (mBtAdapter == null || mBleGatt == null) {
			Log.w("aaaaa", "BluetoothAdapter not initialized");
			TLog.d("readCharacteristic() e false");
			return;
		}

		mBleGatt.readCharacteristic(characteristic);
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

		if (mBtAdapter == null || mBleGatt == null) {
			Log.w("aaaaa", "BluetoothAdapter not initialized");
			TLog.d("setCharacteristicNotification() e false");
			return;
		}

		mBleGatt.setCharacteristicNotification(characteristic, enabled);

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

	public List<BluetoothGattService> getSupportedGattServices() {
		if (mBleGatt == null)
			return null;
		return mBleGatt.getServices();
	}
}
