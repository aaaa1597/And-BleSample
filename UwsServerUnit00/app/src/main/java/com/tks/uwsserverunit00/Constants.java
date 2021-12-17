package com.tks.uwsserverunit00;

import android.bluetooth.BluetoothGatt;
import java.util.UUID;

public class Constants {
	/* エラーコード */
	public final static int UWS_NG_SUCCESS				= 0;	/* OK */
	public final static int UWS_NG_GATT_SUCCESS			= BluetoothGatt.GATT_SUCCESS;
	public final static int UWS_NG_ALREADY_CONNECTED	= -1;	/* 既に接続済 */
	public final static int UWS_NG_SERVICE_NOTFOUND		= -2;	/* サービスが見つからない(=Bluetooth未サポ－ト) */
	public final static int UWS_NG_ADAPTER_NOTFOUND		= -3;	/* BluetoothAdapterがnull(=Bluetooth未サポ－ト) */
	public final static int UWS_NG_BT_OFF				= -4;	/* Bluetooth機能がOFF */
	public final static int UWS_NG_ALREADY_SCANNED		= -5;	/* 既にscan中 */
	public final static int UWS_NG_PERMISSION_DENIED	= -6;	/* 権限なし */
	public final static int UWS_NG_ALREADY_SCANSTOPEDNED= -7;	/* 既にscan停止中 */
	public final static int UWS_NG_ILLEGALARGUMENT		= -8;	/* 引数不正 */
	public final static int UWS_NG_DEVICE_NOTFOUND		= -9;	/* デバイスが見つからない。 */
	public final static int UWS_NG_AIDL_CALLBACK_FAILED = -10;	/* AIDL-callback設定失敗 */
	public final static int UWS_NG_AIDL_INIT_BLE_FAILED = -11;	/* AIDL-BLE初期化失敗 */
	public final static int UWS_NG_AIDL_STARTSCAN_FAILED= -12;	/* AIDL-Scan開始失敗 */

	public static final int BLEMSG_1 = 1;
	public static final int BLEMSG_2 = 2;
	/* ServiceUUIDは0000xxxx-0000-1000-8000-00805f9b34fbの形を守る必要がある。CharacteristicUUIDはなんでもOK.*/
	public static final UUID UWS_SERVICE_UUID					= UUID.fromString("00002c2c-0000-1000-8000-00805f9b34fb");
	public static final UUID UWS_CHARACTERISTIC_HRATBEAT_UUID	= UUID.fromString("29292c2c-728c-4a2b-81cb-7b4d884adb04");

	/* 定義済UUIDに変換する "0000xxxx-0000-1000-8000-00805f9b34fb" */
	private static UUID convertFromInteger(int i) {
		final long MSB = 0x0000000000001000L;
		final long LSB = 0x800000805f9b34fbL;
		long value = i & 0xFFFFFFFF;
		return new UUID(MSB | (value << 32), LSB);
	}
}
