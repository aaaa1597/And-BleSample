package com.tks.uwsserverunit00.ui;

import android.os.RemoteException;
import android.widget.Button;

import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;
import com.google.android.material.snackbar.Snackbar;
import java.text.MessageFormat;
import java.util.Date;
import java.util.List;

import com.tks.uwsserverunit00.DeviceInfo;
import com.tks.uwsserverunit00.IBleServerService;
import com.tks.uwsserverunit00.IBleServerServiceCallback;
import com.tks.uwsserverunit00.R;
import com.tks.uwsserverunit00.TLog;
import static com.tks.uwsserverunit00.Constants.UWS_NG_ALREADY_SCANNED;
import static com.tks.uwsserverunit00.Constants.UWS_NG_SUCCESS;
import static com.tks.uwsserverunit00.Constants.UWS_NG_AIDL_STARTSCAN_FAILED;
import static com.tks.uwsserverunit00.Constants.UWS_NG_AIDL_CALLBACK_FAILED;
import static com.tks.uwsserverunit00.Constants.UWS_NG_AIDL_INIT_BLE_FAILED;

public class FragBleViewModel extends ViewModel {
	private IBleServerService					mBleServiceIf;
	/* ---------------- */
	private final DeviceListAdapter	mDeviceListAdapter = new DeviceListAdapter();
	public DeviceListAdapter getDeviceListAdapter() { return mDeviceListAdapter; }
	public void AddDevice(DeviceInfo deviceInfo) {
		if(deviceInfo != null) mDeviceListAdapter.addDevice(deviceInfo);
	}
	public void AddDeviceList(List<DeviceInfo> deviceInfos)	{
		if(deviceInfos != null && deviceInfos.size() != 0) mDeviceListAdapter.addDevice(deviceInfos);
	}
	/* ---------------- */
	private final MutableLiveData<String>	mScanBtnStr					= new MutableLiveData<>("");
	public MutableLiveData<String>		ScanBtnStr()			{ return mScanBtnStr; }
	/* ---------------- */
	private final MutableLiveData<Boolean>	mNotifyDataSetChanged		= new MutableLiveData<>(false);
	public MutableLiveData<Boolean>		NotifyDataSetChanged()	{ return mNotifyDataSetChanged; }
	/* ---------------- */
	private final MutableLiveData<Integer>	mNotifyItemChanged			= new MutableLiveData<>(-1);
	public MutableLiveData<Integer>		NotifyItemChanged()	{ return mNotifyItemChanged; }
	/* ---------------- */

	/** *****************
	 * サービス接続Callback
	 * *****************/
	public int onServiceConnected(IBleServerService service) {
		mBleServiceIf = service;

		/* コールバック設定 */
		try { mBleServiceIf.setCallback(mCb); }
		catch (RemoteException e) { e.printStackTrace(); return UWS_NG_AIDL_CALLBACK_FAILED;}

		/* BLE初期化 */
		int ret = 0;
		try { ret = mBleServiceIf.initBle(); }
		catch (RemoteException e) { e.printStackTrace(); return UWS_NG_AIDL_INIT_BLE_FAILED;}

		if(ret != UWS_NG_SUCCESS)
			return ret;

		/* scan開始 */
		int retscan = startScan();
		TLog.d("scan開始 ret={0}", retscan);

		return retscan;
	}

	/** *****************
	 * サービス接続Callback
	 * *****************/
	public void onServiceDisconnected() {
		mBleServiceIf = null;
	}

	/** **********
	 * Scan開始
	 * **********/
	public int startScan() {
		/* 既にScan中の時は、開始しない */
		if(mScanBtnStr.getValue().equals("Scan停止"))
			return UWS_NG_ALREADY_SCANNED;

		int ret = 0;
		try { ret = mBleServiceIf.startScan();}
		catch (RemoteException e) { e.printStackTrace();}
		TLog.d("ret={0}", ret);

		if(ret != UWS_NG_SUCCESS)
			return ret;

		try { mBleServiceIf.clearDevice();}
		catch (RemoteException e) { e.printStackTrace(); return UWS_NG_AIDL_STARTSCAN_FAILED;}

		mScanBtnStr.postValue("Scan停止");
		mDeviceListAdapter.clearDevice();
		mNotifyDataSetChanged.postValue(true);

		return UWS_NG_SUCCESS;
	}


	/** **********
	 * Scan終了
	 * **********/
	public void stopScan() {
		int ret;
		try { ret = mBleServiceIf.stopScan();}
		catch (RemoteException e) { e.printStackTrace(); return;}
		TLog.d("scan停止 ret={0}", ret);
		mScanBtnStr.postValue("Scan開始");
	}

	/** *****************
	 * AIDLコールバック
	 * *****************/
	private IBleServerServiceCallback mCb = new IBleServerServiceCallback.Stub() {
		@Override
		public void notifyDeviceInfolist() throws RemoteException {
			List<DeviceInfo> result = mBleServiceIf.getDeviceInfolist();
			mDeviceListAdapter.addDevice(result);
			mNotifyDataSetChanged.postValue(true);
		}

		@Override
		public void notifyDeviceInfo() throws RemoteException {
			DeviceInfo result = mBleServiceIf.getDeviceInfo();
			mDeviceListAdapter.addDevice(result);
			mNotifyDataSetChanged.postValue(true);
			TLog.d("発見!! No:{0}, {1}({2}):Rssi({3})", result.getId(), result.getDeviceAddress(), result.getDeviceName(), result.getDeviceRssi());
		}

		@Override
		public void notifyScanEnd() throws RemoteException {
			TLog.d("scan終了");
		}

		@Override
		public void notifyGattConnected(String Address) throws RemoteException {
			/* TODO */
//			/* Gatt接続完了 */
//			TLog.d("Gatt接続OK!! -> Services探索中. Address={0}", Address);
//			runOnUiThread(() -> { mDeviceListAdapter.setStatus(Address, DeviceListAdapter.ConnectStatus.EXPLORING); });
		}

		@Override
		public void notifyGattDisConnected(String Address) throws RemoteException {
			/* TODO */
//			String logstr = MessageFormat.format("Gatt接続断!! Address={0}", Address);
//			TLog.d(logstr);
//			Snackbar.make(findViewById(R.id.root_view), logstr, Snackbar.LENGTH_LONG).show();
//			runOnUiThread(() -> { mDeviceListAdapter.setStatus(Address, DeviceListAdapter.ConnectStatus.NONE); });
		}

		@Override
		public void notifyServicesDiscovered(String Address, int status) throws RemoteException {
			/* TODO */
//			if(status == Constants.UWS_NG_GATT_SUCCESS) {
//				TLog.d("Services発見. -> 対象Serviceかチェック ret={0}", status);
//				runOnUiThread(() -> { mDeviceListAdapter.setStatus(Address, DeviceListAdapter.ConnectStatus.CHECKAPPLI); });
//			}
//			else {
//				String logstr = MessageFormat.format("Services探索失敗!! 処理終了 ret={0}", status);
//				TLog.d(logstr);
//				runOnUiThread(() -> { mDeviceListAdapter.setStatus(Address, DeviceListAdapter.ConnectStatus.NONE); });
//				Snackbar.make(findViewById(R.id.root_view), logstr, Snackbar.LENGTH_LONG).show();
//			}
		}

		@Override
		public void notifyApplicable(String Address, boolean status) throws RemoteException {
			if(status) {
				TLog.d("対象Chk-OK. -> 通信準備中 Address={0}", Address);
				int pos = mDeviceListAdapter.setStatus(Address, DeviceListAdapter.ConnectStatus.TOBEPREPARED);
				mNotifyItemChanged.postValue(pos);
			}
			else {
				String logstr = MessageFormat.format("対象外デバイス.　処理終了. Address={0}", Address);
				TLog.d(logstr);
				int pos = mDeviceListAdapter.setStatus(Address, DeviceListAdapter.ConnectStatus.NONE);
				mNotifyItemChanged.postValue(pos);
//TODO			Snackbar.make(findViewById(R.id.root_view), logstr, Snackbar.LENGTH_LONG).show();
			}
		}

		@Override
		public void notifyReady2DeviceCommunication(String Address, boolean status) throws RemoteException {
			/* TODO */
//			if(status) {
//				String logstr = MessageFormat.format("BLEデバイス通信 準備完了. Address={0}", Address);
//				TLog.d(logstr);
//				runOnUiThread(() -> { mDeviceListAdapter.setStatus(Address, DeviceListAdapter.ConnectStatus.READY); });
//				Snackbar.make(findViewById(R.id.root_view), logstr, Snackbar.LENGTH_LONG).show();
//			}
//			else {
//				String logstr = MessageFormat.format("BLEデバイス通信 準備失敗!! Address={0}", Address);
//				TLog.d(logstr);
//				runOnUiThread(() -> { mDeviceListAdapter.setStatus(Address, DeviceListAdapter.ConnectStatus.NONE); });
//				Snackbar.make(findViewById(R.id.root_view), logstr, Snackbar.LENGTH_LONG).show();
//			}
		}

		@Override
		public void notifyResRead(String Address, long ldatetime, double longitude, double latitude, int heartbeat, int status) throws RemoteException {
			/* TODO */
			String logstr = MessageFormat.format("デバイス読込成功 {0}=({1} 経度:{2} 緯度:{3} 脈拍:{4}) status={5}", Address, new Date(ldatetime), longitude, latitude, heartbeat, status);
			TLog.d(logstr);
		}

		@Override
		public void notifyFromPeripheral(String Address, long ldatetime, double longitude, double latitude, int heartbeat) throws RemoteException {
			/* TODO */
			String logstr = MessageFormat.format("デバイス通知 {0}=({1} 経度:{2} 緯度:{3} 脈拍:{4})", Address, new Date(ldatetime), longitude, latitude, heartbeat);
			TLog.d(logstr);
		}

		@Override
		public void notifyError(int errcode, String errmsg) throws RemoteException {
			String logstr = MessageFormat.format("ERROR!! errcode={0} : {1}", errcode, errmsg);
			TLog.d(logstr);
//TODO			Snackbar.make(findViewById(R.id.root_view), logstr, Snackbar.LENGTH_LONG).show();
		}
	};
}