package com.test.blesample.peripheral;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

import android.Manifest;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.bluetooth.le.BluetoothLeAdvertiser;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.util.Log;

public class MainActivity extends AppCompatActivity {
	private BluetoothAdapter	mBluetoothAdapter = null;
	ActivityResultLauncher<Intent> mStartForResult = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(),
			result -> {
				Log.d("aaaaa", "bt-onResult() s");
				if (result.getResultCode() == Activity.RESULT_OK) {
					/* Bluetooth機能ONになった */
					Log.d("aaaaa", "Bluetooth OFF -> ON");
					startPeripheral();
				}
				else {
					ErrPopUp.create(MainActivity.this).setErrMsg("Bluetoothを有効にする必要があります。").Show(MainActivity.this);
				}
				Log.d("aaaaa", "bt-onResult() e");
			});
	private final static int REQUEST_PERMISSIONS = 0x1111;

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

	}
}