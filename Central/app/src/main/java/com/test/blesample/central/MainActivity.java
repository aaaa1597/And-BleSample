package com.test.blesample.central;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.support.design.widget.Snackbar;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.widget.Button;
import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity {
	private BluetoothAdapter						mBluetoothAdapter = null;
	private DeviceListAdapter						mDeviceListAdapter;
	private Handler									mHandler;
	private ScanCallback							mScanCallback = null;
	private BluetoothLeScanner						mBLeScanner;
	private final static int  REQUEST_ENABLE_BT		= 0x1111;
	private final static int  REQUEST_PERMISSIONS	= 0x2222;
	private final static long SCAN_PERIOD			= 30000;	/* m秒 */

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);

		/* BLEデバイスリストの初期化 */
		RecyclerView deviceListRvw = findViewById(R.id.rvw_devices);
		deviceListRvw.setHasFixedSize(true);
		deviceListRvw.setLayoutManager(new LinearLayoutManager(getApplicationContext()));
		mDeviceListAdapter = new DeviceListAdapter(new DeviceListAdapter.DeviceListAdapterListener() {
			@Override
			public void onDeviceItemClick(String deviceName, String deviceAddress) {
				/* 接続画面に遷移 */
				mBLeScanner.stopScan(mScanCallback);
				mScanCallback = null;
				Intent intent = new Intent(MainActivity.this, DeviceConnectActivity.class);
				intent.putExtra(DeviceConnectActivity.EXTRAS_DEVICE_NAME	, deviceName);
				intent.putExtra(DeviceConnectActivity.EXTRAS_DEVICE_ADDRESS	, deviceAddress);
				startActivity(intent);
			}
		});
		deviceListRvw.setAdapter(mDeviceListAdapter);

		findViewById(R.id.btnScan).setOnClickListener(view -> {
			startBLEScan();
		});

		/* UIスレッド非同期管理 */
		mHandler = new Handler(Looper.getMainLooper());

		/* Bluetoothのサポート状況チェック 未サポート端末なら起動しない */
		if (!getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
			MsgPopUp.create(MainActivity.this).setErrMsg("Bluetoothが、未サポートの端末です。").Show(MainActivity.this);
		}

		/* 権限が許可されていない場合はリクエスト. */
		if(checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
			requestPermissions(new String[]{Manifest.permission.ACCESS_COARSE_LOCATION}, REQUEST_PERMISSIONS);
		}

		final BluetoothManager bluetoothManager = (BluetoothManager)getSystemService(Context.BLUETOOTH_SERVICE);
		mBluetoothAdapter = bluetoothManager.getAdapter();
		/* Bluetooth未サポート判定 未サポートならエラーpopupで終了 */
		if (mBluetoothAdapter == null) {
			MsgPopUp.create(MainActivity.this).setErrMsg("Bluetoothが、未サポートの端末です。").Show(MainActivity.this);
		}
		/* Bluetooth ON/OFF判定 -> OFFならONにするようにリクエスト */
		else if( !mBluetoothAdapter.isEnabled()) {
			Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
			startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
		}
		else {
			/* Bluetooth機能ONだった */
			startBLEScan();
		}
	}

	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		super.onActivityResult(requestCode, resultCode, data);
		if(requestCode == REQUEST_ENABLE_BT) {
			/* Bluetooth機能ONになった。 */
			startBLEScan();
		}
		else {
			MsgPopUp.create(MainActivity.this).setErrMsg("Bluetooth機能をONにする必要があります。").Show(MainActivity.this);
		}
	}

	/*
	start Bluetooth Low Energy scan
	 */
	private void startBLEScan() {
		TLog.d("s");

		if(mScanCallback != null) {
			TLog.d("e すでにscan中。");
			return;
		}

		/* Bluetooth機能がONになってないのでreturn */
		if( !mBluetoothAdapter.isEnabled()) {
			TLog.d("e Bluetooth機能がOFFってる。");
			Snackbar.make(findViewById(R.id.root_view), "Bluetooth機能をONにして下さい。", Snackbar.LENGTH_LONG).show();
			return;
		}
		TLog.d("Bluetooth ON.");

		/* Bluetooth使用の権限がないのでreturn */
		if(checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
			TLog.d( "startPrepare() e Bluetooth使用の権限が拒否られた。");
			MsgPopUp.create(MainActivity.this).setErrMsg("このアプリに権限を与えて下さい。").Show(MainActivity.this);
			return;
		}
		TLog.d( "Bluetooth使用権限OK.");

		TLog.d("scan開始");
		mDeviceListAdapter.clearDevice();
		runOnUiThread(() -> {
			Button btn = findViewById(R.id.btnScan);
			btn.setText("scan中");
			btn.setEnabled(false);
		});

		mScanCallback = new ScanCallback() {
			@Override
			public void onBatchScanResults(List<ScanResult> results) {
				super.onBatchScanResults(results);
				mDeviceListAdapter.addDevice(results);
//				for(ScanResult result : results) {
//					TLog.d("---------------------------------- size=" + results.size());
//					TLog.d("aaaaa AdvertisingSid             =" + result.getAdvertisingSid());
//					TLog.d("aaaaa device                     =" + result.getDevice());
//					TLog.d("            Name                 =" + result.getDevice().getName());
//					TLog.d("            Address              =" + result.getDevice().getAddress());
//					TLog.d("            Class                =" + result.getDevice().getClass());
//					TLog.d("            BluetoothClass       =" + result.getDevice().getBluetoothClass());
//					TLog.d("            BondState            =" + result.getDevice().getBondState());
//					TLog.d("            Type                 =" + result.getDevice().getType());
//					TLog.d("            Uuids                =" + result.getDevice().getUuids());
//					TLog.d("aaaaa DataStatus                 =" + result.getDataStatus());
//					TLog.d("aaaaa PeriodicAdvertisingInterval=" + result.getPeriodicAdvertisingInterval());
//					TLog.d("aaaaa PrimaryPhy                 =" + result.getPrimaryPhy());
//					TLog.d("aaaaa Rssi                       =" + result.getRssi());
//					TLog.d("aaaaa ScanRecord                 =" + result.getScanRecord());
//					TLog.d("aaaaa TimestampNanos             =" + result.getTimestampNanos());
//					TLog.d("aaaaa TxPower                    =" + result.getTxPower());
//					TLog.d("aaaaa Class                      =" + result.getClass());
//					TLog.d("----------------------------------");
//				}
			}

			@Override
			public void onScanResult(int callbackType, ScanResult result) {
				super.onScanResult(callbackType, result);
				mDeviceListAdapter.addDevice(result);
				if(result.getScanRecord() != null && result.getScanRecord().getServiceUuids() != null)
					TLog.d("発見!! {0}({1}):Rssi({2}) Uuids({3})", result.getDevice().getAddress(), result.getDevice().getName(), result.getRssi(), result.getScanRecord().getServiceUuids().toString());
				else
					TLog.d("発見!! {0}({1}):Rssi({2})", result.getDevice().getAddress(), result.getDevice().getName(), result.getRssi());
//				if(result !=null && result.getDevice() != null) {
//					TLog.d("----------------------------------");
//					TLog.d("aaaaa AdvertisingSid             =" + result.getAdvertisingSid());				//aaaaa AdvertisingSid             =255
//					TLog.d("aaaaa device                     =" + result.getDevice());						//aaaaa device                     =65:57:FE:6F:E6:D9
//					TLog.d("            Name                 =" + result.getDevice().getName());				//            Name                 =null
//					TLog.d("            Address              =" + result.getDevice().getAddress());			//            Address              =65:57:FE:6F:E6:D9
//					TLog.d("            Class                =" + result.getDevice().getClass());				//            Class                =class android.bluetooth.BluetoothDevice
//					TLog.d("            BluetoothClass       =" + result.getDevice().getBluetoothClass());	//            BluetoothClass       =0
//					TLog.d("            BondState            =" + result.getDevice().getBondState());			//            BondState            =10
//					TLog.d("            Type                 =" + result.getDevice().getType());				//            Type                 =0
//					TLog.d("            Uuids                =" + result.getDevice().getUuids());				//            Uuids                =null
//					TLog.d("aaaaa DataStatus                 =" + result.getDataStatus());					//aaaaa DataStatus                 =0
//					TLog.d("aaaaa PeriodicAdvertisingInterval=" + result.getPeriodicAdvertisingInterval());	//aaaaa PeriodicAdvertisingInterval=0
//					TLog.d("aaaaa PrimaryPhy                 =" + result.getPrimaryPhy());					//aaaaa PrimaryPhy                 =1
//					TLog.d("aaaaa Rssi                       =" + result.getRssi());							//aaaaa Rssi                       =-79
//					TLog.d("aaaaa ScanRecord                 =" + result.getScanRecord());					//aaaaa ScanRecord                 =ScanRecord [mAdvertiseFlags=26, mServiceUuids=null, mServiceSolicitationUuids=[], mManufacturerSpecificData={76=[16, 5, 25, 28, 64, 39, -24]}, mServiceData={}, mTxPowerLevel=12, mDeviceName=null]
//					TLog.d("aaaaa TimestampNanos             =" + result.getTimestampNanos());				//aaaaa TimestampNanos             =13798346768432
//					TLog.d("aaaaa TxPower                    =" + result.getTxPower());						//aaaaa TxPower                    =127
//					TLog.d("aaaaa Class                      =" + result.getClass());							//aaaaa Class                      =class android.bluetooth.le.ScanResult
//					TLog.d("----------------------------------");
//				}
			}

			@Override
			public void onScanFailed(int errorCode) {
				super.onScanFailed(errorCode);
				TLog.d("aaaaa scan失敗!! errorCode=" + errorCode);
			}
		};

		/* Bluetoothサポート有,Bluetooth使用権限有,Bluetooth ONなので、セントラルとして起動 */
		mBLeScanner = mBluetoothAdapter.getBluetoothLeScanner();

		mHandler.postDelayed(() -> {
			mBLeScanner.stopScan(mScanCallback);
			Button btn = findViewById(R.id.btnScan);
			btn.setText("scan開始");
			btn.setEnabled(true);
			TLog.d("scan終了");
			mScanCallback = null;
		}, SCAN_PERIOD);

		/* scanフィルタ */
		List<ScanFilter> scanFilters = new ArrayList<>();
		scanFilters.add(new ScanFilter.Builder().build());

		/* scan設定 */
		ScanSettings.Builder scansetting = new ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_POWER);

		/* scan開始 */
		mBLeScanner.startScan(scanFilters, scansetting.build(), mScanCallback);
	}
}
