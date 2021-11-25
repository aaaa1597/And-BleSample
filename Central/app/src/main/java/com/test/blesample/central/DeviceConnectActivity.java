package com.test.blesample.central;

import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.graphics.Color;
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
import static com.test.blesample.central.Constants.BODY_SENSOR_LOCATION_CHARACTERISTIC_UUID;
import static com.test.blesample.central.Constants.HEART_RATE_SERVICE_UUID;
import static com.test.blesample.central.Constants.SERVER_MSG_FIRST_STATE;
import static com.test.blesample.central.Constants.SERVER_MSG_SECOND_STATE;

public class DeviceConnectActivity extends AppCompatActivity {
	public static final String EXTRAS_DEVICE_NAME	= "DEVICE_NAME";
	public static final String EXTRAS_DEVICE_ADDRESS= "DEVICE_ADDRESS";

	private final ArrayList<ArrayList<BluetoothGattCharacteristic>> mDeviceServices = new ArrayList<>();
	private CentralService				mBluetoothLeService;
	private BluetoothGattCharacteristic	mCharacteristic;
	private String						mDeviceAddress;

	/* BLE管理のService */
	private final ServiceConnection mCon = new ServiceConnection() {
		@Override
		public void onServiceConnected(ComponentName componentName, IBinder service) {
			mBluetoothLeService = ((CentralService.LocalBinder)service).getService();
			if (!mBluetoothLeService.initialize()) {
				Log.e("aaaaa", "Unable to initialize Bluetooth");
				finish();
			}

			// Automatically connects to the device upon successful start-up initialization.
			mBluetoothLeService.connect(mDeviceAddress);
		}
		@Override
		public void onServiceDisconnected(ComponentName componentName) {
			mBluetoothLeService = null;
		}
	};

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_device_connect);

		/* 読出し要求ボタン */
        findViewById(R.id.btn_req_read_characteristic).setOnClickListener(view -> {
			requestReadCharacteristic();
		});

		/* BLE通信用サービス起動 */
		Intent gattServiceIntent = new Intent(this, CentralService.class);
		bindService(gattServiceIntent, mCon, BIND_AUTO_CREATE);

		/* 引継ぎデータ取得 */
		Intent intent = getIntent();
		if(intent == null)
			return;

		/* デバイス名設定 */
		String deviceName = intent.getStringExtra(EXTRAS_DEVICE_NAME);
		((TextView)findViewById(R.id.connected_device_name)).setText(TextUtils.isEmpty(deviceName) ? "" : deviceName);

		/* デバイスaddress保持 */
		mDeviceAddress  = intent.getStringExtra(EXTRAS_DEVICE_ADDRESS);
	}


	@Override
	protected void onResume() {
		super.onResume();
		registerReceiver(mGattIntentListner, makeGattUpdateIntentFilter());
	}


	@Override
	protected void onPause() {
		super.onPause();
		unregisterReceiver(mGattIntentListner);
	}


	@Override
	protected void onDestroy() {
		super.onDestroy();
		unbindService(mCon);
		mBluetoothLeService = null;
	}

	/*
	request from the Server the value of the Characteristic.
	this request is asynchronous.
	 */
	private void requestReadCharacteristic() {
		if (mBluetoothLeService != null && mCharacteristic != null) {
			mBluetoothLeService.readCharacteristic(mCharacteristic);
		} else {
			Snackbar.make(findViewById(R.id.root_view_device), "Unknown error.", Snackbar.LENGTH_LONG).show();
		}
	}

	/*
	 Handles various events fired by the Service.
	 ACTION_GATT_CONNECTED: connected to a GATT server.
	 ACTION_GATT_DISCONNECTED: disconnected from a GATT server.
	 ACTION_GATT_SERVICES_DISCOVERED: discovered GATT services.
	 ACTION_DATA_AVAILABLE: received data from the device.  This can be a result of read or notification operations.
	*/
	private final BroadcastReceiver mGattIntentListner = new BroadcastReceiver() {
		@Override
		public void onReceive(Context context, Intent intent) {

			String action = intent.getAction();

			if (action == null) {
				return;
			}

			switch (intent.getAction()) {

				case CentralService.UWS_GATT_CONNECTED:
					updateConnectionState(R.string.connected);
                    findViewById(R.id.btn_req_read_characteristic).setEnabled(true);
					break;

				case CentralService.UWS_GATT_DISCONNECTED:
					updateConnectionState(R.string.disconnected);
                    findViewById(R.id.btn_req_read_characteristic).setEnabled(false);
					break;


				case CentralService.UWS_GATT_SERVICES_DISCOVERED:
					// set all the supported services and characteristics on the user interface.
					setGattServices(mBluetoothLeService.getSupportedGattServices());
					registerCharacteristic();
					break;

				case CentralService.UWS_DATA_AVAILABLE:
					int msg = intent.getIntExtra(CentralService.UWS_DATA, -1);
					Log.v("aaaaa", "ACTION_DATA_AVAILABLE " + msg);
					updateInputFromServer(msg);
					break;

			}
		}
	};


	/*
	 This sample demonstrates 'Read' and 'Notify' features.
	 See http://d.android.com/reference/android/bluetooth/BluetoothGatt.html for the complete
	 list of supported characteristic features.
	*/
	private void registerCharacteristic() {

		BluetoothGattCharacteristic characteristic = null;

		/* iterate all the Services the connected device offer.
		a Service is a collection of Characteristic.
		 */
		for (ArrayList<BluetoothGattCharacteristic> service : mDeviceServices) {

			// iterate all the Characteristic of the Service
			for (BluetoothGattCharacteristic serviceCharacteristic : service) {

				/* check this characteristic belongs to the Service defined in
				PeripheralAdvertiseService.buildAdvertiseData() method
				 */
				if (serviceCharacteristic.getService().getUuid().equals(HEART_RATE_SERVICE_UUID)) {

					if (serviceCharacteristic.getUuid().equals(BODY_SENSOR_LOCATION_CHARACTERISTIC_UUID)) {
						characteristic = serviceCharacteristic;
						mCharacteristic = characteristic;
					}
				}
			}
		}

	   /*
		int charaProp = characteristic.getProperties();
		if ((charaProp | BluetoothGattCharacteristic.PROPERTY_READ) > 0) {
		*/

		if (characteristic != null) {
			mBluetoothLeService.readCharacteristic(characteristic);
			mBluetoothLeService.setCharacteristicNotification(characteristic, true);
		}
	}


	/*
	Demonstrates how to iterate through the supported GATT Services/Characteristics.
	*/
	private void setGattServices(List<BluetoothGattService> gattServices) {

		if (gattServices == null) {
			return;
		}

		mDeviceServices.clear();

		// Loops through available GATT Services from the connected device
		for (BluetoothGattService gattService : gattServices) {
			ArrayList<BluetoothGattCharacteristic> characteristic = new ArrayList<>();
			characteristic.addAll(gattService.getCharacteristics()); // each GATT Service can have multiple characteristic
			mDeviceServices.add(characteristic);
		}

	}


	private void updateConnectionState(final int resourceId) {
		runOnUiThread(new Runnable() {
			@Override
			public void run() {
				((TextView)findViewById(R.id.connection_status)).setText(resourceId);
			}
		});
	}


	private static IntentFilter makeGattUpdateIntentFilter() {
		final IntentFilter intentFilter = new IntentFilter();
		intentFilter.addAction(CentralService.UWS_GATT_CONNECTED);
		intentFilter.addAction(CentralService.UWS_GATT_DISCONNECTED);
		intentFilter.addAction(CentralService.UWS_GATT_SERVICES_DISCOVERED);
		intentFilter.addAction(CentralService.UWS_DATA_AVAILABLE);
		return intentFilter;
	}


	private void updateInputFromServer(int msg) {

		String color;

		switch (msg) {
			case SERVER_MSG_FIRST_STATE:
				color = "#AD1457";
				break;

			case SERVER_MSG_SECOND_STATE:
				color = "#6A1B9A";
				break;

			default:
				color = "#FFFFFF";
				break;

		}

		((ImageView)findViewById(R.id.imv_characteristic_value)).setBackgroundColor(Color.parseColor(color));
		Snackbar.make(findViewById(R.id.root_view_device), "Characteristic value received: "+msg, Snackbar.LENGTH_LONG).show();
	}
}
