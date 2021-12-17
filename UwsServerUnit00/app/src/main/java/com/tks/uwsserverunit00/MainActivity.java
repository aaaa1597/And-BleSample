package com.tks.uwsserverunit00;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.fragment.app.FragmentActivity;
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModelProvider;
import android.Manifest;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import com.google.android.material.snackbar.Snackbar;
import com.tks.uwsserverunit00.ui.FragBleViewModel;
import com.tks.uwsserverunit00.ui.FragMapViewModel;

import java.util.Arrays;

public class MainActivity extends FragmentActivity {
	private FragBleViewModel	mBleViewModel;
	private FragMapViewModel	mMapViewModel;
	private final static int	REQUEST_PERMISSIONS = 1111;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);
		TLog.d("");
		mMapViewModel = new ViewModelProvider(this).get(FragMapViewModel.class);
		mBleViewModel = new ViewModelProvider(this).get(FragBleViewModel.class);
		mBleViewModel.ShowSnacbar().observe(this, showMsg -> {
			Snackbar.make(findViewById(R.id.root_view), showMsg, Snackbar.LENGTH_LONG).show();
		});

		/* Bluetoothのサポート状況チェック 未サポート端末なら起動しない */
		if (!getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
			ErrPopUp.create(MainActivity.this).setErrMsg("Bluetoothが、未サポートの端末です。\n終了します。").Show(MainActivity.this);
		}

		/* 地図権限とBluetooth権限が許可されていない場合はリクエスト. */
		if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
			if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
				requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT}, REQUEST_PERMISSIONS);
			else
				requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, REQUEST_PERMISSIONS);
		}

		/* Bluetooth ON/OFF判定 */
		final BluetoothManager bluetoothManager = (BluetoothManager)getSystemService(Context.BLUETOOTH_SERVICE);
		BluetoothAdapter bluetoothAdapter = bluetoothManager.getAdapter();
		/* Bluetooth未サポート判定 未サポートならエラーpopupで終了 */
		if (bluetoothAdapter == null)
			ErrPopUp.create(MainActivity.this).setErrMsg("Bluetoothが、未サポートの端末です。").Show(MainActivity.this);
		/* OFFならONにするようにリクエスト */
		else if( !bluetoothAdapter.isEnabled()) {
			Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
			ActivityResultLauncher<Intent> startForResult = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(),
					result -> {
						if(result.getResultCode() != Activity.RESULT_OK) {
							ErrPopUp.create(MainActivity.this).setErrMsg("BluetoothがOFFです。ONにして操作してください。\n終了します。").Show(MainActivity.this);
						}
						else {
							bindBleService(mCon);
						}
					});
			startForResult.launch(enableBtIntent);
		}

		bindBleService(mCon);
	}

	@Override
	public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
		super.onRequestPermissionsResult(requestCode, permissions, grantResults);

		/* 対象外なので、無視 */
		if (requestCode != REQUEST_PERMISSIONS) return;

		/* 権限リクエストの結果を取得する. */
		long ngcnt = Arrays.stream(grantResults).filter(value -> value != PackageManager.PERMISSION_GRANTED).count();
		if (ngcnt > 0) {
			ErrPopUp.create(MainActivity.this).setErrMsg("このアプリには必要な権限です。\n再起動後に許可してください。\n終了します。").Show(MainActivity.this);
			return;
		}
		else {
			mMapViewModel.Permission().postValue(true);
			bindBleService(mCon);
		}
	}

	@Override
	protected void onDestroy() {
		super.onDestroy();
		TLog.d("onDestroy()");
		unbindService(mCon);
	}

	/** **********
	 * サービスBind
	 * **********/
	private void bindBleService(ServiceConnection con) {
		/* Bluetooth未サポート */
		if (!getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
			TLog.d("Bluetooth未サポートの端末.何もしない.");
			return;
		}

		/* 権限が許可されていない */
		if(checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
			TLog.d("Bluetooth権限なし.何もしない.");
			return;
		}

		/* Bluetooth未サポート */
		final BluetoothManager bluetoothManager = (BluetoothManager)getSystemService(Context.BLUETOOTH_SERVICE);
		if(bluetoothManager == null) {
			TLog.d("Bluetooth未サポートの端末.何もしない.");
			return;
		}

		/* Bluetooth未サポート */
		BluetoothAdapter bluetoothAdapter = bluetoothManager.getAdapter();
		if (bluetoothAdapter == null) {
			TLog.d("Bluetooth未サポートの端末.何もしない.");
			return;
		}

		/* Bluetooth ON/OFF判定 */
		if( !bluetoothAdapter.isEnabled()) {
			TLog.d("Bluetooth OFF.何もしない.");
			return;
		}

		/* Bluetoothサービス起動 */
		Intent intent = new Intent(MainActivity.this, BleServerService.class);
		bindService(intent, con, Context.BIND_AUTO_CREATE);
		TLog.d("Bluetooth使用クリア -> Bluetoothサービス起動");
	}

	/* Serviceコールバック */
	private final ServiceConnection	mCon = new ServiceConnection() {
		@Override
		public void onServiceConnected(ComponentName name, IBinder service) {
			int ret = mBleViewModel.onServiceConnected(IBleServerService.Stub.asInterface(service));
			TLog.d("Bletooth初期化 ret={0}", ret);

			if(ret == Constants.UWS_NG_PERMISSION_DENIED)
				ErrPopUp.create(MainActivity.this).setErrMsg("このアプリに権限がありません!!\n終了します。").Show(MainActivity.this);
			else if(ret == Constants.UWS_NG_SERVICE_NOTFOUND)
				ErrPopUp.create(MainActivity.this).setErrMsg("この端末はBluetoothに対応していません!!\n終了します。").Show(MainActivity.this);
			else if(ret == Constants.UWS_NG_ADAPTER_NOTFOUND)
				ErrPopUp.create(MainActivity.this).setErrMsg("この端末はBluetoothに対応していません!!\n終了します。").Show(MainActivity.this);
			else if(ret == Constants.UWS_NG_BT_OFF)
				Snackbar.make(findViewById(R.id.root_view), "BluetoothがOFFです。\nONにして操作してください。", Snackbar.LENGTH_LONG).show();
			else if(ret == Constants.UWS_NG_ALREADY_SCANNED)
				Snackbar.make(findViewById(R.id.root_view), "すでにscan中です。継続します。", Snackbar.LENGTH_LONG).show();
			else if(ret != Constants.UWS_NG_SUCCESS)
				ErrPopUp.create(MainActivity.this).setErrMsg("原因不明のエラーが発生しました!!\n終了します。").Show(MainActivity.this);
			return;
		}

		@Override
		public void onServiceDisconnected(ComponentName name) {
			mBleViewModel.onServiceDisconnected();
		}
	};
}
