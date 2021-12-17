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
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanResult;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Binder;
import android.os.IBinder;
import android.os.RemoteException;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import static com.tks.uwsserverunit00.Constants.UWS_NG_SUCCESS;
import static com.tks.uwsserverunit00.Constants.UWS_CHARACTERISTIC_HRATBEAT_UUID;
import static com.tks.uwsserverunit00.Constants.UWS_NG_DEVICE_NOTFOUND;

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
	private BluetoothGattCharacteristic			mCharacteristic = null;
	private final BluetoothGattCallback			mGattCallback = new BluetoothGattCallback() {
		@Override
		public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
			TLog.d("BluetoothGattCallback::onConnectionStateChange() {0} -> {1}", status, newState);
			TLog.d("BluetoothProfile.STATE_CONNECTING({0}) STATE_CONNECTED({1}) STATE_DISCONNECTING({2}) STATE_DISCONNECTED({3})", BluetoothProfile.STATE_CONNECTING, BluetoothProfile.STATE_CONNECTED, BluetoothProfile.STATE_DISCONNECTING, BluetoothProfile.STATE_DISCONNECTED);
			/* Gattサーバ接続完了 */
			if(newState == BluetoothProfile.STATE_CONNECTED) {
				try { mCb.notifyGattConnected(gatt.getDevice().getAddress()); }
				catch (RemoteException e) { e.printStackTrace(); }
				TLog.d("GATTサーバ接続OK.");
				mBleGatt.discoverServices();
				TLog.d("Discovery開始");
			}
			/* Gattサーバ断 */
			else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
				TLog.d("GATTサーバ断.");
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
			TLog.d("Services一致!! address={0} ret={1}", address, status);

			try { mCb.notifyServicesDiscovered(gatt.getDevice().getAddress(), status); }
			catch (RemoteException e) { e.printStackTrace(); }


			if (status == BluetoothGatt.GATT_SUCCESS) {
				mCharacteristic = findTerget(gatt, Constants.UWS_SERVICE_UUID, Constants.UWS_CHARACTERISTIC_HRATBEAT_UUID);
				if (mCharacteristic != null) {
					try { mCb.notifyApplicable(address, true); }
					catch (RemoteException e) { e.printStackTrace(); }

					TLog.d("find it. Services and Characteristic.");
					boolean ret1 = gatt.readCharacteristic(mCharacteristic);	/* 初回読み出し */
					boolean ret2 = gatt.setCharacteristicNotification(mCharacteristic, true);
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
			TLog.d("読込み要求の応答 status=", status);
			if (status == BluetoothGatt.GATT_SUCCESS) {
				parseRcvData(gatt, characteristic);
			}
			else {
				TLog.d("onCharacteristicRead GATT_FAILURE");
			}
			TLog.d("BluetoothGattCallback::onCharacteristicRead() e");
		}

		@Override
		public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
			TLog.d("ペリフェラルからの受信");
			parseRcvData(gatt, characteristic);
		}
	};

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

	/* データ受信(peripheral -> Service -> Activity) */
	private void parseRcvData(BluetoothGatt gatt, final BluetoothGattCharacteristic characteristic) {
		if (UWS_CHARACTERISTIC_HRATBEAT_UUID.equals(characteristic.getUuid())) {
			/* 受信データ取出し */
			int flag = characteristic.getProperties();
			int format = ((flag & 0x01) != 0) ? BluetoothGattCharacteristic.FORMAT_UINT16 : BluetoothGattCharacteristic.FORMAT_UINT8;
			int msg = characteristic.getIntValue(format, 0);
			TLog.d("message: {0}", msg);
			/* 受信データ取出し */
			try { mCb.notifyResRead(gatt.getDevice().getAddress(), new Date().getTime(), 0, 0, msg, 0); }
			catch (RemoteException e) { e.printStackTrace(); }
		}
	}

	@Nullable
	@Override
	public IBinder onBind(Intent intent) {
		TLog.d("");
		return mBinder;
	}

	@Override
	public boolean onUnbind(Intent intent) {
		disconnectBleDevice();
		return super.onUnbind(intent);
	}

	/* Bluetooth接続 */
	private int connectBleDevice(final String address) {
		if (address == null) {
			TLog.d("デバイスアドレスなし");
			return UWS_NG_DEVICE_NOTFOUND;
		}

		/* 初回接続 */
		BluetoothDevice device = mBluetoothAdapter.getRemoteDevice(address);
		if (device == null) {
			TLog.d("デバイス({0})が見つかりません。接続できませんでした。", address);
			return UWS_NG_DEVICE_NOTFOUND;
		}

		/* デバイスに直接接続したい時に、autoConnectをfalseにする。 */
		/* デバイスが使用可能になったら自動的にすぐに接続する様にする時に、autoConnectをtrueにする。 */
		mBleGatt = device.connectGatt(getApplicationContext(), false, mGattCallback);
		TLog.d("GATTサーバ接続開始.address={0}", address);

		return UWS_NG_SUCCESS;
	}

	public void disconnectBleDevice() {
		if (mBleGatt != null) {
			mBleGatt.disconnect();
			mBleGatt.close();
			mBleGatt = null;
		}
	}

	private void readCharacteristic(BluetoothGattCharacteristic characteristic) {
		if (mBleGatt == null) {
			TLog.d("Bluetooth not initialized");
			throw new IllegalStateException("Error!! Bluetooth not initialized!!");
		}

		mBleGatt.readCharacteristic(characteristic);
	}

	/* 指定CharacteristicのCallback登録 */
	public void setCharacteristicNotification(BluetoothGattCharacteristic characteristic, boolean enabled) {
		TLog.d("setCharacteristicNotification() s");
		if (mBleGatt == null) {
			TLog.d("Bluetooth not initialized");
			throw new IllegalStateException("Error!! Bluetooth not initialized!!");
		}

		mBleGatt.setCharacteristicNotification(characteristic, enabled);
		TLog.d("setCharacteristicNotification() e");
	}

	/* 対象デバイスの保有するサービスを取得 */
	public List<BluetoothGattService> getSupportedGattServices() {
		if (mBleGatt == null)
			return null;
		return mBleGatt.getServices();
	}

	/************/
	/*  Scan実装 */
	/************/
	private IBleServerServiceCallback mCb = null;	/* 常に後発のみ */
	private DeviceInfo			mTmpDeviceInfo;
	private List<DeviceInfo>	mTmpDeviceInfoList = new ArrayList<>();
	private BluetoothLeScanner	mBLeScanner;

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
		public int stopScan() throws RemoteException {
			return BsvStopScan();
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
		public int connectDevice(String deviceAddress) throws RemoteException {
			return 0;/* TODO これは別に作る */
		}

		@Override
		public void clearDevice() throws RemoteException {
			mTmpDeviceInfoList.clear();
			mTmpDeviceInfo = null;
		}

		@Override
		public int connectBleDevice(String deviceAddress) throws RemoteException {
			return BleServerService.this.connectBleDevice(deviceAddress);
		}

		@Override
		public void readCharacteristic(BluetoothGattCharacteristic charac) throws RemoteException {
			BleServerService.this.readCharacteristic(charac);
		}

		@Override
		public List<BluetoothGattService> getSupportedGattServices() throws RemoteException {
			return BleServerService.this.getSupportedGattServices();
		}

		@Override
		public void setCharacteristicNotification(BluetoothGattCharacteristic charac, boolean ind) throws RemoteException {
			BleServerService.this.setCharacteristicNotification(charac, ind);
		}
	};

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
						if(retUuisStr.startsWith(Constants.UWS_SERVICE_UUID.toString().substring(0,5))) {
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
					if(retUuisStr.startsWith(Constants.UWS_SERVICE_UUID.toString().substring(0,5))) {
						isApplicable = true;
						id = Integer.decode("0x"+retUuisStr.substring(6,8));
					}
				}
				else {
					TLog.d("retUuisStr is null");
				}
				mTmpDeviceInfo = new DeviceInfo(result.getDevice().getName(), result.getDevice().getAddress(), result.getRssi(), isApplicable, id);
				if(result.getScanRecord() != null && result.getScanRecord().getServiceUuids() != null)
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

		/* scan開始 */
		mBLeScanner = mBluetoothAdapter.getBluetoothLeScanner();
		mBLeScanner.startScan(mScanCallback);

		return UWS_NG_SUCCESS;
	}

	/** *******
	 * Scan終了
	 * ********/
	private int BsvStopScan() {
		if(mScanCallback == null)
			return Constants.UWS_NG_ALREADY_SCANSTOPEDNED;

		mBLeScanner.stopScan(mScanCallback);
		mScanCallback = null;
		TLog.d("scan終了");
		try { mCb.notifyScanEnd();}
		catch (RemoteException e) { e.printStackTrace(); }

		return UWS_NG_SUCCESS;
	}
}
