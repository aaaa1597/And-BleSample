package com.tks.uwsserverunit00;

import androidx.annotation.Nullable;
import android.Manifest;
import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanResult;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Binder;
import android.os.IBinder;
import android.os.RemoteException;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import static com.tks.uwsserverunit00.Constants.UWS_NG_SUCCESS;
import static com.tks.uwsserverunit00.Constants.UWS_NG_ALREADY_CONNECTED;
import static com.tks.uwsserverunit00.Constants.UWS_NG_DEVICE_NOTFOUND;
import static com.tks.uwsserverunit00.Constants.UWS_SERVICE_UUID;
import static com.tks.uwsserverunit00.Constants.UWS_CHARACTERISTIC_HRATBEAT_UUID;

/**
 * -30 dBm	素晴らしい	達成可能な最大信号強度。クライアントは、これを実現するには、APから僅か数フィートである必要があります。現実的には一般的ではなく、望ましいものでもありません	N/A
 * -60 dBm	良好	非常に信頼性の高く、データパケットのタイムリーな伝送を必要とするアプリケーションのための最小信号強度	VoIP/VoWiFi, ストリーミングビデオ
 * -70 dBm	Ok	信頼できるパケット伝送に必要な最小信号強度	Email, web
 * -80 dBm	よくない	基本的なコネクティビティに必要な最小信号強度。パケット伝送は信頼できない可能性があります	N/A
 * -90 dBm	使用不可	ノイズレベルに近いかそれ以下の信号強度。殆ど機能しない	N/A
 **/

public class BleServerService extends Service {
	private BluetoothAdapter					mBluetoothAdapter;
	private BluetoothGatt						mBleGatt;
	private final Map<String, BluetoothGatt>	mConnectedDevices = new HashMap<>();
	private IBleServerServiceCallback			mCb;	/* 常に後発優先 */

	@Nullable
	@Override
	public IBinder onBind(Intent intent) {
		TLog.d("");
		return mBinder;
	}

	@Override
	public boolean onUnbind(Intent intent) {
		BsvClearDevice();
		if (mBleGatt != null) {
			mBleGatt.disconnect();
			mBleGatt.close();
			mBleGatt = null;
		}
		return super.onUnbind(intent);
	}

	/** *****
	 * Binder
	 * ******/
	private Binder mBinder = new IBleServerService.Stub() {
		@Override
		public void setCallback(IBleServerServiceCallback callback) throws RemoteException {
			mCb = callback;
		}

		@Override
		public int initBle() throws RemoteException {
			return BsvInit();
		}

		@Override
		public int startScan() throws RemoteException {
			return BsvStartScan();
		}

		@Override
		public List<DeviceInfo> getDeviceInfolist() throws RemoteException {
			return mTmpDeviceInfoList;
		}

		@Override
		public DeviceInfo getDeviceInfo() throws RemoteException {
			return mTmpDeviceInfo;
		}

		@Override
		public int stopScan() throws RemoteException {
			return BsvStopScan();
		}

		@Override
		public int connectDevice(String deviceAddress) throws RemoteException {
			return BsvConnectDevice(deviceAddress);
		}

//		まだ不要
//		@Override
//		void disconnectDevice(String deviceAddress) {
//			BsvDisConnectDevice(deviceAddress);
//		}

		@Override
		public void clearDevice() throws RemoteException {
			BsvClearDevice();
		}
	};

	/************/
	/*  Scan実装 */
	/************/
	private DeviceInfo					mTmpDeviceInfo;
	private List<DeviceInfo>			mTmpDeviceInfoList = new ArrayList<>();

	/** *******
	 * BLE初期化
	 * ********/
	int BsvInit() {
		/* Bluetooth権限なし */
		if(checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED)
			return Constants.UWS_NG_PERMISSION_DENIED;
		TLog.d( "Bluetooth権限OK.");

		/* Bluetoothサービス取得 */
		final BluetoothManager bluetoothManager = (BluetoothManager)getSystemService(Context.BLUETOOTH_SERVICE);
		if(bluetoothManager == null)
			return Constants.UWS_NG_SERVICE_NOTFOUND;
		TLog.d( "Bluetoothサービス取得OK.");

		/* Bluetoothアダプタ取得 */
		mBluetoothAdapter = bluetoothManager.getAdapter();
		if (mBluetoothAdapter == null)
			return Constants.UWS_NG_ADAPTER_NOTFOUND;

		/* Bluetooth ON */
		if( !mBluetoothAdapter.isEnabled())
			return Constants.UWS_NG_BT_OFF;
		TLog.d( "Bluetooth ON.");

		return UWS_NG_SUCCESS;
	}

	/** *******
	 * Scan開始
	 * ********/
	private ScanCallback		mScanCallback = null;
	private int BsvStartScan() {
		/* 既にscan中 */
		if(mScanCallback != null)
			return Constants.UWS_NG_ALREADY_SCANNED;

		/* Bluetooth機能がOFF */
		if( !mBluetoothAdapter.isEnabled())
			return Constants.UWS_NG_BT_OFF;

		TLog.d("Bluetooth ON.");

		TLog.d("scan開始");
		mScanCallback = new ScanCallback() {
			@Override
			public void onBatchScanResults(List<ScanResult> results) {
				super.onBatchScanResults(results);
				mTmpDeviceInfoList = results.stream().map(ret -> {
					boolean isApplicable = false;
					int id = -1;
					if(ret.getScanRecord()!=null && ret.getScanRecord().getServiceUuids()!=null) {
						String retUuisStr = ret.getScanRecord().getServiceUuids().get(0).toString();
						if(retUuisStr.startsWith(UWS_SERVICE_UUID.toString().substring(0,5))) {
							isApplicable = true;
							id = Integer.decode("0x"+retUuisStr.substring(6,8));
						}
					}
					return new DeviceInfo(ret.getDevice().getName(), ret.getDevice().getAddress(), ret.getRssi(), isApplicable, id);
				}).collect(Collectors.toList());
				try { mCb.notifyDeviceInfolist();}
				catch (RemoteException e) {e.printStackTrace();}
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
				boolean isApplicable = false;
				int id = -1;
				if(result.getScanRecord()!=null && result.getScanRecord().getServiceUuids()!=null) {
					String retUuisStr = result.getScanRecord().getServiceUuids().get(0).toString();
					TLog.d("retUuisStr={0}", retUuisStr);
					if(retUuisStr.startsWith(UWS_SERVICE_UUID.toString().substring(0,5))) {
						isApplicable = true;
						id = Integer.decode("0x"+retUuisStr.substring(6,8));
					}
				}
				else {
					TLog.d("retUuisStr is null");
				}
				mTmpDeviceInfo = new DeviceInfo(result.getDevice().getName(), result.getDevice().getAddress(), result.getRssi(), isApplicable, id);
				TLog.d("発見!! {0}({1}):Rssi({2}) ScanRecord={3}", result.getDevice().getAddress(), result.getDevice().getName(), result.getRssi(), result.getScanRecord());
				try { mCb.notifyDeviceInfo();}
				catch (RemoteException e) {e.printStackTrace();}

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
				TLog.d("scan失敗!! errorCode=" + errorCode);
			}
		};

//		/* scanフィルタ */
//		List<ScanFilter> scanFilters = new ArrayList<>();
//		scanFilters.add(new ScanFilter.Builder().build());
//
//		/* scan設定 */
//		ScanSettings.Builder scansetting = new ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_POWER);

		/* scan開始 */
		mBluetoothAdapter.getBluetoothLeScanner().startScan(mScanCallback);

		return UWS_NG_SUCCESS;
	}

	/** *******
	 * Scan終了
	 * ********/
	private int BsvStopScan() {
		if(mScanCallback == null)
			return Constants.UWS_NG_ALREADY_SCANSTOPEDNED;

		mBluetoothAdapter.getBluetoothLeScanner().stopScan(mScanCallback);
		mScanCallback = null;
		TLog.d("scan終了");
		try { mCb.notifyScanEnd();}
		catch (RemoteException e) { e.printStackTrace(); }

		return UWS_NG_SUCCESS;
	}

	/** *************
	 * BLEデバイス接続
	 ** *************/
	private int BsvConnectDevice(final String address) {
		if(address == null || address.equals("")) {
			TLog.d("デバイスアドレスなし");
			return UWS_NG_DEVICE_NOTFOUND;
		}

		/* 接続済判定 */
		BluetoothGatt gat = mConnectedDevices.get(address);
		if(gat != null) {
			TLog.d("接続済デバイス({0})　何もしない", address);
			return UWS_NG_ALREADY_CONNECTED;
//			boolean ret = gat.connect();
		}

		TLog.d("デバイス初回接続({0})", address);
		/* 初回接続 */
		BluetoothDevice device = mBluetoothAdapter.getRemoteDevice(address);
		if (device == null) {
			TLog.d("デバイス({0})が見つかりません。接続できませんでした。", address);
			return UWS_NG_DEVICE_NOTFOUND;
		}

		/* デバイスに直接接続したい時に、autoConnectをfalseにする。 */
		/* デバイスが使用可能になったら自動的にすぐに接続する様にする時に、autoConnectをtrueにする。 */
		mBleGatt = device.connectGatt(getApplicationContext(), false, mGattCallback);
		if (mBleGatt == null) {
			TLog.d("Gattサーバ接続失敗!! address={0}", address);
			return UWS_NG_DEVICE_NOTFOUND;
		}

		/* ここでは、追加しない。Service発見後に追加する */
//		mConnectedDevices.put(blegatt.getDevice().getAddress(), blegatt);

		/* Gatt接続中 */
		TLog.d("Gattサーバ接続成功. address={0} gattAddress={1}", address, mBleGatt.getDevice().getAddress());

		return UWS_NG_SUCCESS;
	}

	private void BsvClearDevice() {
		mConnectedDevices.forEach((addr, gat) -> {
			gat.disconnect();
			gat.close();
		});
		mConnectedDevices.clear();
		mTmpDeviceInfoList.clear();
		mTmpDeviceInfo = null;
	}

	/** ***************
	 * Gattコールバック
	 ** ***************/
	private final BluetoothGattCallback mGattCallback = new BluetoothGattCallback() {
		@Override
		public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
			TLog.d("BluetoothGattCallback::onConnectionStateChange() {0} -> {1}", status, newState);
			TLog.d("BluetoothProfile.STATE_CONNECTING({0}) STATE_CONNECTED({1}) STATE_DISCONNECTING({2}) STATE_DISCONNECTED({3})", BluetoothProfile.STATE_CONNECTING, BluetoothProfile.STATE_CONNECTED, BluetoothProfile.STATE_DISCONNECTING, BluetoothProfile.STATE_DISCONNECTED);
			/* Gatt接続完了 */
			if(newState == BluetoothProfile.STATE_CONNECTED) {
				try { mCb.notifyGattConnected(gatt.getDevice().getAddress()); }
				catch (RemoteException e) { e.printStackTrace(); }
				TLog.d("Gatt接続OK. address={0}", gatt.getDevice().getAddress());
				gatt.discoverServices();
				TLog.d("Discovery開始 address={0}", gatt.getDevice().getAddress());
			}
			/* Gattサーバ断 */
			else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
				TLog.d("Gatt断.");
				try { mCb.notifyGattDisConnected(gatt.getDevice().getAddress()); }
				catch (RemoteException e) { e.printStackTrace(); }

				TLog.d("GATT 再接続");
				gatt.disconnect();
				gatt.close();
				mBleGatt = gatt.getDevice().connectGatt(getApplicationContext(), false, mGattCallback);
			}
		}

		@Override
		public void onServicesDiscovered(BluetoothGatt gatt, int status) {
			String address = gatt.getDevice().getAddress();
			TLog.d("Services発見!! address={0} ret={1}", address, status);

			try { mCb.notifyServicesDiscovered(gatt.getDevice().getAddress(), status); }
			catch (RemoteException e) { e.printStackTrace(); }

			if (status == BluetoothGatt.GATT_SUCCESS) {
				BluetoothGattCharacteristic charac = findTerget(gatt, UWS_SERVICE_UUID, UWS_CHARACTERISTIC_HRATBEAT_UUID);
				if (charac != null) {
					try { mCb.notifyApplicable(address, true); }
					catch (RemoteException e) { e.printStackTrace(); }

					TLog.d("find it. Services and Characteristic.");
					boolean ret1 = gatt.readCharacteristic(charac);	/* 初回読み出し */
					boolean ret2 = gatt.setCharacteristicNotification(charac, true);
					if(ret1 && ret2) {
						TLog.d("BLEデバイス通信 準備完了. address={0}", address);
						try { mCb.notifyReady2DeviceCommunication(address, true); }
						catch (RemoteException e) { e.printStackTrace(); }
						mConnectedDevices.put(address, gatt);
					}
					else {
						TLog.d("BLEデバイス通信 準備失敗!! address={0}", address);
						try { mCb.notifyReady2DeviceCommunication(address, false); }
						catch (RemoteException e) { e.printStackTrace(); }
						mConnectedDevices.remove(address);
						gatt.disconnect();
						gatt.close();
					}
				}
				else {
					TLog.d("対象外デバイス!! address={0}", address);
					try { mCb.notifyApplicable(address, false); }
					catch (RemoteException e) { e.printStackTrace(); }
					mConnectedDevices.remove(address);
					gatt.disconnect();
					gatt.close();
				}
			}
		}

		/* 読込み要求の応答 */
		@Override
		public void onCharacteristicRead(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
			TLog.d("読込み要求の応答 status={0}", status);
			if (status == BluetoothGatt.GATT_SUCCESS) {
				Object[] rcvval = parseRcvData(characteristic);
				try { mCb.notifyResRead(gatt.getDevice().getAddress(), (long)rcvval[0], (double)rcvval[1], (double)rcvval[2], (int)rcvval[3], status); }
				catch (RemoteException e) { e.printStackTrace(); }
				TLog.d("読込み要求の応答 rcvval=({0},{1},{2},{3}) status={4} BluetoothGatt.GATT_SUCCESS({5}) BluetoothGatt.GATT_FAILURE({6})", new Date((long)rcvval[0]), (double)rcvval[1], (double)rcvval[2], (int)rcvval[3], status, BluetoothGatt.GATT_SUCCESS, BluetoothGatt.GATT_FAILURE);
			}
			else {
				TLog.d("GATT_FAILURE");
				try { mCb.notifyResRead(gatt.getDevice().getAddress(), new Date().getTime(), 0, 0, 0, status); }
				catch (RemoteException e) { e.printStackTrace(); }
				TLog.d("読込み要求の応答 status={1} BluetoothGatt.GATT_SUCCESS({2}) BluetoothGatt.GATT_FAILURE({3})", status, BluetoothGatt.GATT_SUCCESS, BluetoothGatt.GATT_FAILURE);
			}
		}

		@Override
		public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
			Object[] rcvval = parseRcvData(characteristic);
			TLog.d("ペリフェラルからの受信 rcvval=({0} 経度:{1} 緯度:{2} 脈拍:{3})", new Date((long)rcvval[0]), (double)rcvval[1], (double)rcvval[2], (int)rcvval[3]);
			try { mCb.notifyFromPeripheral(gatt.getDevice().getAddress(), (long)rcvval[0], (double)rcvval[1], (double)rcvval[2], (int)rcvval[3]); }
			catch (RemoteException e) { e.printStackTrace(); }
		}
	};

	/** ***********************
	 * 対象のCharacteristicを検索
	 ** ***********************/
	private BluetoothGattCharacteristic findTerget(BluetoothGatt gatt, UUID ServiceUuid, UUID CharacteristicUuid) {
		BluetoothGattCharacteristic ret = null;
		for (BluetoothGattService service : gatt.getServices()) {
			/*-*-----------------------------------*/
			for(BluetoothGattCharacteristic gattChara : service.getCharacteristics())
				TLog.d("{0} : service-UUID={1} Chara-UUID={2}", gatt.getDevice().getAddress(), service.getUuid(), gattChara.getUuid());
			/*-*-----------------------------------*/
			if( !service.getUuid().toString().startsWith(ServiceUuid.toString().substring(0,5)))
				continue;

			final List<BluetoothGattCharacteristic> findc = service.getCharacteristics().stream().filter(c -> {
				return c.getUuid().equals(CharacteristicUuid);
			}).collect(Collectors.toList());
			/* 見つかった最後の分が有効なので、上書きする */
			ret = findc.get(0);
		}
		return ret;
	}

	/** *********
	 * データParse
	 ** *********/
	private Object[] parseRcvData(final BluetoothGattCharacteristic characteristic) {
		if (UWS_CHARACTERISTIC_HRATBEAT_UUID.equals(characteristic.getUuid())) {
			/* 受信データ取出し */
			final byte[] rcvdata = characteristic.getValue();
			TLog.d("rcvdata:(size={0}, {1})", rcvdata.length, Arrays.toString(rcvdata));
			if(rcvdata.length == 28) {
				/* 全データ受信(日付,経度,緯度,脈拍) */
				long	ldatetime	= ByteBuffer.wrap(rcvdata).getLong();
				double	longitude	= ByteBuffer.wrap(rcvdata).getDouble(8);
				double	latitude	= ByteBuffer.wrap(rcvdata).getDouble(16);
				int		heartbeat	= ByteBuffer.wrap(rcvdata).getInt(24);
				TLog.d("受信データ=({0} 経度:{1} 緯度:{2} 脈拍:{3})", new Date(ldatetime), longitude, latitude, heartbeat);
				return new Object[] {ldatetime, longitude, latitude, heartbeat};
			}
			short seqno = ByteBuffer.wrap(rcvdata).getShort(2);
			if(seqno == 0) {
				/* 先行の通知データ(msgid,seqno,日付,経度)受信 */
				short	msgid		= ByteBuffer.wrap(rcvdata).getShort();
//				short	seqno		= ByteBuffer.wrap(rcvdata).getShort(2);
				long	ldatetime	= ByteBuffer.wrap(rcvdata).getLong(4);
				double	longitude	= ByteBuffer.wrap(rcvdata).getDouble(12);
				TLog.d("受信データ=({0} 経度:{1} 緯度:{2} 脈拍:{3})", new Date(ldatetime), longitude, 999, 999);
				return new Object[] {ldatetime, longitude, 999.0, 999};
			}
			else {
				/* 後発の通知データ(msgid,seqno,緯度,脈拍)受信 */
				short	msgid		= ByteBuffer.wrap(rcvdata).getShort();
//				short	seqno		= ByteBuffer.wrap(rcvdata).getShort(2);
				double	latitude	= ByteBuffer.wrap(rcvdata).getDouble(4);
				int		heartbeat	= ByteBuffer.wrap(rcvdata).getInt(12);
				TLog.d("受信データ=({0} 経度:{1} 緯度:{2} 脈拍:{3})", new Date(0), 999, latitude, heartbeat);
				return new Object[] {999, 999.0, latitude, heartbeat};
			}
		}
		return new Object[] {new Date().getTime(),0,0,0};
	}
}
