package com.test.blesample.central;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

import android.Manifest;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanResult;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.util.Log;
import android.widget.Toast;

public class MainActivity extends AppCompatActivity {
	private BluetoothAdapter	mBluetoothAdapter = null;
	private BluetoothLeScanner  mBluetoothLeScanner = null;
	private ScanCallback		mScanCallback = new ScanCallback() {
		@Override
		public void onScanResult(int callbackType, ScanResult result) {
			super.onScanResult(callbackType, result);
			if(result !=null && result.getDevice() != null) {
				Log.d("aaaaa","aaaaa AdvertisingSid             =" + result.getAdvertisingSid());
				Log.d("aaaaa","aaaaa devices                    =" + result.getDevice());
				Log.d("aaaaa","aaaaa DataStatus                 =" + result.getDataStatus());
				Log.d("aaaaa","aaaaa PeriodicAdvertisingInterval=" + result.getPeriodicAdvertisingInterval());
				Log.d("aaaaa","aaaaa PrimaryPhy                 =" + result.getPrimaryPhy());
				Log.d("aaaaa","aaaaa Rssi                       =" + result.getRssi());
				Log.d("aaaaa","aaaaa ScanRecord                 =" + result.getScanRecord());
				Log.d("aaaaa","aaaaa TimestampNanos             =" + result.getTimestampNanos());
				Log.d("aaaaa","aaaaa TxPower                    =" + result.getTxPower());
				Log.d("aaaaa","aaaaa Class                      =" + result.getClass());
			}
		}

		@Override
		public void onScanFailed(int errorCode) {
			super.onScanFailed(errorCode);
			Log.d("aaaaa","aaaaa Bluetoothのscan失敗!! errorCode=" + errorCode);
		}
	};

	ActivityResultLauncher<Intent> mStartForResult = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(),
			result -> {
				if (result.getResultCode() == Activity.RESULT_OK) {
					Log.d("aaaaa", "Bluetooth OFF -> ON");
				}
				else {
					ErrPopUp.create(MainActivity.this).setErrMsg("Bluetoothを有効にする必要があります。").Show(MainActivity.this);
				}
			});
	private final static int REQUEST_PERMISSIONS = 1;

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
		if(checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED){
			requestPermissions(new String[]{Manifest.permission.ACCESS_COARSE_LOCATION},REQUEST_PERMISSIONS);
		}

		final BluetoothManager bluetoothManager = (BluetoothManager)getSystemService(Context.BLUETOOTH_SERVICE);
		mBluetoothAdapter = bluetoothManager.getAdapter();
		if (mBluetoothAdapter == null) {
			ErrPopUp.create(MainActivity.this).setErrMsg("Bluetoothが、未サポートの端末です。").Show(MainActivity.this);
			return;
		}
		else if( !mBluetoothAdapter.isEnabled()) {
			Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
			mStartForResult.launch(enableBtIntent);
		}

		mBluetoothLeScanner = mBluetoothAdapter.getBluetoothLeScanner();
		mBluetoothLeScanner.startScan(mScanCallback);
	}

	@Override
	public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
		/* 権限リクエストの結果を取得する. */
		if (requestCode == REQUEST_PERMISSIONS) {
			if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
				Toast.makeText(MainActivity.this, "Succeed", Toast.LENGTH_SHORT).show();
			} else {
				ErrPopUp.create(MainActivity.this).setErrMsg("失敗しました。\n\"許可\"を押下して、このアプリにBluetoothの権限を与えて下さい。\n終了します。").Show(MainActivity.this);
			}
		}else {
			super.onRequestPermissionsResult(requestCode, permissions, grantResults);
		}
	}
}