package com.tks.uwsclient;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

import android.Manifest;
import android.app.Activity;
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
import android.os.Build;
import android.os.Bundle;
import android.widget.RadioGroup;
import android.widget.Switch;

import java.nio.ByteBuffer;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Date;
import java.util.HashSet;
import java.util.Queue;

import static com.tks.uwsclient.Constants.UWS_SERVICE_UUID;
import static com.tks.uwsclient.Constants.UWS_CHARACTERISTIC_SAMLE_UUID;
import static com.tks.uwsclient.Constants.BLEMSG_1;
import static com.tks.uwsclient.Constants.BLEMSG_2;

import com.google.android.material.snackbar.Snackbar;

public class MainActivity extends AppCompatActivity {
	private BluetoothGattCharacteristic		mUwsCharacteristic;
	private BluetoothGattServer				mGattServer;
	private final HashSet<BluetoothDevice>	mBluetoothDevices = new HashSet<>();
	private Queue<Runnable>					mNotifySender = new ArrayDeque<>();
	private short							mMsgIdCounter = 0;
	private final static int  REQUEST_ENABLE_BT		= 0x1111;
	private final static int  REQUEST_PERMISSIONS	= 0x2222;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);

		/* 通知ボタン(Centralへ送信) */
		findViewById(R.id.button_notify).setOnClickListener(view -> {
			/* 通知データの一括生成 */
			boolean indicate = (mUwsCharacteristic.getProperties() & BluetoothGattCharacteristic.PROPERTY_INDICATE) == BluetoothGattCharacteristic.PROPERTY_INDICATE;
			for (BluetoothDevice device : mBluetoothDevices) {
				/* 通知データ(先行分(msgid,seqno,日付,経度)) */
				mNotifySender.add(() -> {
					set1stValuetoCharacteristic(mUwsCharacteristic, mMsgIdCounter  , new Date(), 221);
					TLog.d("1st送信");
					mGattServer.notifyCharacteristicChanged(device, mUwsCharacteristic, indicate);
				});
				/* 通知データ(後発分(msgid,seqno,緯度,脈拍)) */
				mNotifySender.add(() -> {
					set2ndValuetoCharacteristic(mUwsCharacteristic, mMsgIdCounter++, 222, 100);
					TLog.d("2nd送信");
					mGattServer.notifyCharacteristicChanged(device, mUwsCharacteristic, indicate);
				});
			}
			/* 生成した通知データを1件処理(後は、onNotificationSent()で送信する) */
			mNotifySender.poll().run();
			TLog.d("Centralへ通知 mNotifySender.size()={0}", mNotifySender.size());
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
			int heartbeat = checkedId == R.id.rbn_value_1 ? BLEMSG_1 : BLEMSG_2;
			Snackbar.make(findViewById(R.id.root_view), "もう、何もしなくなった。", Snackbar.LENGTH_LONG).show();
//			setValuetoCharacteristic(mUwsCharacteristic, heartbeat);
		});

		/* Bluetoothのサポート状況チェック 未サポート端末なら起動しない */
		if (!getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
			ErrPopUp.create(MainActivity.this).setErrMsg("Bluetoothが、未サポートの端末です。").Show(MainActivity.this);
		}

		/* Bluetooth権限が許可されていない場合はリクエスト. */
		if (checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
			if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
				requestPermissions(new String[]{Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.BLUETOOTH_ADVERTISE, Manifest.permission.BLUETOOTH_CONNECT}, REQUEST_PERMISSIONS);
			else
				requestPermissions(new String[]{Manifest.permission.ACCESS_COARSE_LOCATION}, REQUEST_PERMISSIONS);
		}

		final BluetoothManager bluetoothManager = (BluetoothManager)getSystemService(Context.BLUETOOTH_SERVICE);
		BluetoothAdapter bluetoothAdapter = bluetoothManager.getAdapter();
		/* Bluetooth未サポート判定 未サポートならエラーpopupで終了 */
		if (bluetoothAdapter == null) {
			ErrPopUp.create(MainActivity.this).setErrMsg("Bluetoothが、未サポートの端末です。").Show(MainActivity.this);
		}
		/* Bluetooth ON/OFF判定 -> OFFならONにするようにリクエスト */
		else if( !bluetoothAdapter.isEnabled()) {
			Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
			ActivityResultLauncher<Intent> startForResult = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(),
					result -> {
						if(result.getResultCode() != Activity.RESULT_OK) {
							ErrPopUp.create(MainActivity.this).setErrMsg("BluetoothがOFFです。ONにして操作してください。\n終了します。").Show(MainActivity.this);
						}
						else {
							/* Bluetooth機能ONだった */
							mGattServer = bluetoothManager.openGattServer(this, mGattServerCallback);
							if(mGattServer == null) {
								ErrPopUp.create(MainActivity.this).setErrMsg("Ble初期化に失敗!!\n終了します。再起動で直る可能性があります。").Show(MainActivity.this);
								finish();
							}
							/* 自分自身のペリフェラル特性を定義 */
							mUwsCharacteristic = defineOwnCharacteristic();
						}
					});
			startForResult.launch(enableBtIntent);
		}
		else {
			/* Bluetooth機能ONだった */
			mGattServer = bluetoothManager.openGattServer(this, mGattServerCallback);
			if(mGattServer == null) {
				ErrPopUp.create(MainActivity.this).setErrMsg("Ble初期化に失敗!!\n終了します。再起動で直る可能性があります。").Show(MainActivity.this);
				finish();
			}
			/* 自分自身のペリフェラル特性を定義 */
			mUwsCharacteristic = defineOwnCharacteristic();
		}
	}

	/* 自身のペリフェラル特性を定義 */
	private BluetoothGattCharacteristic defineOwnCharacteristic() {
		/* 自身が提供するサービスを定義 */
		BluetoothGattService ownService = new BluetoothGattService(UWS_SERVICE_UUID, BluetoothGattService.SERVICE_TYPE_PRIMARY);

		/* 自身が提供するCharacteristic(特性)を定義 : 通知と読込みに対し、読込み許可 */
		BluetoothGattCharacteristic charac = new BluetoothGattCharacteristic(/*UUID*/UWS_CHARACTERISTIC_SAMLE_UUID,
																			BluetoothGattCharacteristic.PROPERTY_NOTIFY | BluetoothGattCharacteristic.PROPERTY_READ,
																	/*permissions*/BluetoothGattCharacteristic.PERMISSION_READ);

		charac.setValue(new byte[]{'0','1','2','3','4','5','6','7','8','9','a','b','c','d','e','f',16,17,18,19,20,21,22,23,24,25,26,27});

		/* 定義したサービスにCharacteristic(特性)を付与 */
		ownService.addCharacteristic(charac);

		/* Gattサーバに定義したサービスを付与 */
		mGattServer.addService(ownService);

		return charac;
	}

	private void set1stValuetoCharacteristic(BluetoothGattCharacteristic charac, short msgid, Date datetime, double longitude/*経度*/) {
		TLog.d("1st送信データ生成");
		byte[] ret = new byte[20];
		int spos = 0;
		/* メッセージID(2byte) */
		byte[] bmsgid = s2bs(msgid);
		System.arraycopy(bmsgid, 0, ret, spos, bmsgid.length);
		spos += bmsgid.length;
		/* Seq番号(2byte) */
		byte[] bseqno = s2bs((short)0);
		System.arraycopy(bseqno, 0, ret, spos, bseqno.length);
		spos += bseqno.length;
		/* 日付(8byte) */
		byte[] bdatetime = l2bs(datetime.getTime());
		System.arraycopy(bdatetime, 0, ret, spos, bdatetime.length);
		spos += bdatetime.length;
		/* 経度(8byte) */
		byte[] blongitude = d2bs(longitude);
		System.arraycopy(blongitude, 0, ret, spos, blongitude.length);
		/* 値 設定 */
		charac.setValue(ret);
	}

	private void set2ndValuetoCharacteristic(BluetoothGattCharacteristic charac, short msgid, double latitude/*緯度*/, int heartbeat) {
		TLog.d("2nd送信データ生成");
		byte[] ret = new byte[16];
		int spos = 0;
		/* メッセージID(2byte) */
		byte[] bmsgid = s2bs(msgid);
		System.arraycopy(bmsgid, 0, ret, spos, bmsgid.length);
		spos += bmsgid.length;
		/* Seq番号(2byte) */
		byte[] bseqno = s2bs((short)1);
		System.arraycopy(bseqno, 0, ret, spos, bseqno.length);
		spos += bseqno.length;
		/* 緯度(8byte) */
		byte[] blatitude = d2bs(latitude);
		System.arraycopy(blatitude, 0, ret, spos, blatitude.length);
		spos += blatitude.length;
		/* 脈拍(4byte) */
		byte[] bheartbeat = i2bs(heartbeat);
		System.arraycopy(bheartbeat, 0, ret, spos, bheartbeat.length);
		/* 値 設定 */
		charac.setValue(ret);
	}
	private void setValuetoCharacteristic(BluetoothGattCharacteristic charac, Date datetime, double longitude/*経度*/, double latitude/*緯度*/, int heartbeat) {
		byte[] ret = new byte[28];
		int spos = 0;
		/* 日付(8byte) */
		byte[] bdatetime = l2bs(datetime.getTime());
		System.arraycopy(bdatetime, 0, ret, spos, bdatetime.length);
		spos += bdatetime.length;
		/* 経度(8byte) */
		byte[] blongitude = d2bs(longitude);
		System.arraycopy(blongitude, 0, ret, spos, blongitude.length);
		/* 緯度(8byte) */
		byte[] blatitude = d2bs(latitude);
		System.arraycopy(blatitude, 0, ret, spos, blatitude.length);
		spos += blatitude.length;
		/* 脈拍(4byte) */
		byte[] bheartbeat = i2bs(heartbeat);
		System.arraycopy(bheartbeat, 0, ret, spos, bheartbeat.length);
		/* 値 設定 */
		charac.setValue(ret);
	}
	private byte[] s2bs(short value) {
		return ByteBuffer.allocate(2).putShort(value).array();
	}
	private byte[] i2bs(int value) {
		return ByteBuffer.allocate(4).putInt(value).array();
	}
	private byte[] l2bs(long value) {
		return ByteBuffer.allocate(8).putLong(value).array();
	}
	private byte[] d2bs(double value) {
		return ByteBuffer.allocate(8).putDouble(value).array();
	}

	private final BluetoothGattServerCallback mGattServerCallback = new BluetoothGattServerCallback() {
		/** ++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
		 * 接続状態変化通知
		 * @param device	サーバ側デバイス
		 * @param status	int: Status of the connect or disconnect operation. BluetoothGatt.GATT_SUCCESS if the operation succeeds.
		 * @param newState	BluetoothProfile.STATE_DISCONNECTED, BluetoothProfile#STATE_CONNECTED
		 * ++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++*/
		@Override
		public void onConnectionStateChange(BluetoothDevice device, final int status, int newState) {
			super.onConnectionStateChange(device, status, newState);
			TLog.d("status={0} newState={1}", status, newState);
			TLog.d("status-BluetoothGatt.GATT_SUCCESS({0}) newState-BluetoothGatt.STATE_xxxx(STATE_CONNECTED({1}),STATE_DISCONNECTED({2}))", BluetoothGatt.GATT_SUCCESS, BluetoothGatt.STATE_CONNECTED, BluetoothGatt.STATE_DISCONNECTED);
			if (status == BluetoothGatt.GATT_SUCCESS) {
				if (newState == BluetoothGatt.STATE_CONNECTED) {
					mBluetoothDevices.add(device);
					TLog.d("接続 to device: {0}", device.getAddress());
				}
				else if (newState == BluetoothGatt.STATE_DISCONNECTED) {
					mBluetoothDevices.remove(device);
					TLog.d("切断 from device");
				}
			}
			else {
				mBluetoothDevices.remove(device);
				TLog.e("{0} : {1}",getString(R.string.status_error_when_connecting), status);
				ErrPopUp.create(MainActivity.this).setErrMsg(getString(R.string.status_error_when_connecting) + ":" + status).Show(MainActivity.this);
			}
		}

		/* 通知/指示受信 */
		@Override
		public void onNotificationSent(BluetoothDevice device, int status) {
			super.onNotificationSent(device, status);
			/* 積まれた通知データを送信 */
			Runnable runner = mNotifySender.poll();
			if(runner != null) runner.run();
			TLog.d("Notification sent. Status:{0} 残:{1}",status, mNotifySender.size());
		}

		/* Read要求受信 */
		@Override
		public void onCharacteristicReadRequest(BluetoothDevice device, int requestId, int offset, BluetoothGattCharacteristic characteristic) {
			super.onCharacteristicReadRequest(device, requestId, offset, characteristic);
			/* 初回送信時のみ値設定 */
			if(requestId == 1)
				setValuetoCharacteristic(mUwsCharacteristic, new Date(), 555, 666,  123);
			/* 分割送信処理対応 */
			byte[] resData = new byte[characteristic.getValue().length-offset];
			System.arraycopy(characteristic.getValue(), offset, resData, 0, resData.length);

			TLog.d("CentralからのRead要求({0}) 返却値:(UUID:{1},resData(byte数{2}:データ{3}) org(offset{4},val:{5}))", requestId, characteristic.getUuid(), resData.length, Arrays.toString(resData), offset, Arrays.toString(characteristic.getValue()));
			mGattServer.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, 0, resData);
		}


		/* Write要求受信 */
		@Override
		public void onCharacteristicWriteRequest(BluetoothDevice device, int requestId, BluetoothGattCharacteristic characteristic, boolean preparedWrite, boolean responseNeeded, int offset, byte[] value) {
			super.onCharacteristicWriteRequest(device, requestId, characteristic, preparedWrite, responseNeeded, offset, value);

			TLog.d("CentralからのWrite要求 受信値:(UUID:{0},vat:{1}))", mUwsCharacteristic.getUuid(), Arrays.toString(value));
			setValuetoCharacteristic(mUwsCharacteristic, new Date(), 555, 666,  123);
			if (responseNeeded)
				mGattServer.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, 0, value);
		}

		/* Read要求受信 */
		@Override
		public void onDescriptorReadRequest(BluetoothDevice device, int requestId, int offset, BluetoothGattDescriptor descriptor) {
			super.onDescriptorReadRequest(device, requestId, offset, descriptor);

			TLog.d("CentralからのDescriptor_Read要求 返却値:(UUID:{0},vat:{1}))", descriptor.getUuid(), Arrays.toString(descriptor.getValue()));
			mGattServer.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, descriptor.getValue());
		}

		/* Write要求受信 */
		@Override
		public void onDescriptorWriteRequest(BluetoothDevice device, int requestId,
											 BluetoothGattDescriptor descriptor, boolean preparedWrite, boolean responseNeeded,
											 int offset,
											 byte[] value) {
			super.onDescriptorWriteRequest(device, requestId, descriptor, preparedWrite, responseNeeded, offset, value);

			TLog.d("CentralからのDescriptor_Write要求 受信値:(UUID:{0},vat:{1}))", descriptor.getUuid(), Arrays.toString(value));

//            int status = BluetoothGatt.GATT_SUCCESS;
//            if (descriptor.getUuid() == CLIENT_CHARACTERISTIC_CONFIGURATION_UUID) {
//                BluetoothGattCharacteristic characteristic = descriptor.getCharacteristic();
//                boolean supportsNotifications = (characteristic.getProperties() & BluetoothGattCharacteristic.PROPERTY_NOTIFY) != 0;
//                boolean supportsIndications   = (characteristic.getProperties() & BluetoothGattCharacteristic.PROPERTY_INDICATE) != 0;
//
//                if (!(supportsNotifications || supportsIndications)) {
//                    status = BluetoothGatt.GATT_REQUEST_NOT_SUPPORTED;
//                }
//                else if (value.length != 2) {
//                    status = BluetoothGatt.GATT_INVALID_ATTRIBUTE_LENGTH;
//                }
//                else if (Arrays.equals(value, BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE)) {
//                    status = BluetoothGatt.GATT_SUCCESS;
//                    mCurrentServiceFragment.notificationsDisabled(characteristic);
//                    descriptor.setValue(value);
//                }
//                else if (supportsNotifications &&
//                        Arrays.equals(value, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)) {
//                    status = BluetoothGatt.GATT_SUCCESS;
//                    mCurrentServiceFragment.notificationsEnabled(characteristic, false /* indicate */);
//                    descriptor.setValue(value);
//                }
//                else if (supportsIndications &&
//                        Arrays.equals(value, BluetoothGattDescriptor.ENABLE_INDICATION_VALUE)) {
//                    status = BluetoothGatt.GATT_SUCCESS;
//                    mCurrentServiceFragment.notificationsEnabled(characteristic, true /* indicate */);
//                    descriptor.setValue(value);
//                }
//                else {
//                    status = BluetoothGatt.GATT_REQUEST_NOT_SUPPORTED;
//                }
//            }
//            else {
//                status = BluetoothGatt.GATT_SUCCESS;
//                descriptor.setValue(value);
//            }
            if (responseNeeded)
                mGattServer.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS,0,null);

		}
	};
}
