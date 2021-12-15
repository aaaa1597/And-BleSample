package com.tks.uwsserverunit00.ui;

import android.bluetooth.le.ScanResult;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import java.util.ArrayList;
import java.util.List;

import com.tks.uwsserverunit00.DeviceInfo;
import com.tks.uwsserverunit00.R;

/**
 * -30 dBm	素晴らしい	達成可能な最大信号強度。クライアントは、これを実現するには、APから僅か数フィートである必要があります。現実的には一般的ではなく、望ましいものでもありません	N/A
 * -60 dBm	良好	非常に信頼性の高く、データパケットのタイムリーな伝送を必要とするアプリケーションのための最小信号強度	VoIP/VoWiFi, ストリーミングビデオ
 * -70 dBm	Ok	信頼できるパケット伝送に必要な最小信号強度	Email, web
 * -80 dBm	よくない	基本的なコネクティビティに必要な最小信号強度。パケット伝送は信頼できない可能性があります	N/A
 * -90 dBm	使用不可	ノイズレベルに近いかそれ以下の信号強度。殆ど機能しない	N/A
 * */

public class DeviceListAdapter extends RecyclerView.Adapter<DeviceListAdapter.ViewHolder> {
	static class ViewHolder extends RecyclerView.ViewHolder {
		TextView	mTxtDeviceName;
		TextView	mTxtDeviceNameAddress;
		ImageView	mImvRssi;
		ImageView	mImvConnectStatus;
		TextView	mTxtId;
		TextView	mTxtHertBeat;
		Button		mBtnConnect;
		ImageButton	mBtnBuoy;
		ViewHolder(View view) {
			super(view);
			mTxtDeviceName			= view.findViewById(R.id.device_name);
			mTxtDeviceNameAddress	= view.findViewById(R.id.device_address);
			mImvRssi				= view.findViewById(R.id.imvRssi);
			mImvConnectStatus		= view.findViewById(R.id.imvConnectStatus);
			mTxtId					= view.findViewById(R.id.txtId);
			mTxtHertBeat			= view.findViewById(R.id.txtHertBeat);
			mBtnConnect				= view.findViewById(R.id.btnConnect);
			mBtnBuoy				= view.findViewById(R.id.btnBuoy);
		}
	}

	/* インターフェース : DeviceListAdapterListener */
	public interface DeviceListAdapterListener {
		void onDeviceItemClick(View view, String deviceName, String deviceAddress);
	}

	/* メンバ変数 */
	private ArrayList<ScanResult>		mDeviceList = new ArrayList<ScanResult>();
	private DeviceListAdapterListener	mListener;

	public enum ConnectStatus { NONE, CONNECTING, EXPLORING, CHECKAPPLI, TOBEPREPARED, READY}
	private static class DevicveInfoModel {
		public String			mDeviceName;
		public String			mDeviceAddress;
		public int				mDeviceRssi;
		public ConnectStatus	mConnectStatus;
		public int				mId;
		public int				mHertBeat;
		public  boolean			mIsApplicable;
		public DevicveInfoModel(String devicename, String deviceaddress, int devicerssi, ConnectStatus status, int hertbeat, boolean isApplicable, int id) {
			mDeviceName		= devicename;
			mDeviceAddress	= deviceaddress;
			mDeviceRssi		= devicerssi;
			mConnectStatus	= status;
			mId				= id;
			mHertBeat		= hertbeat;
			mIsApplicable	= isApplicable;
		}
	}

	/* コンストラクタ */
	public DeviceListAdapter(DeviceListAdapterListener listener) {
		mListener = listener;
	}

	@NonNull
	@Override
	public DeviceListAdapter.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
		View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.listitem_devices, parent, false);
		return new ViewHolder(view);
	}

	@Override
	public void onBindViewHolder(@NonNull DeviceListAdapter.ViewHolder holder, int position) {
		ScanResult scanResult = mDeviceList.get(position);
		final String deviceName		= scanResult.getDevice().getName();
		final String deviceAddress	= scanResult.getDevice().getAddress();

		holder.mTxtDeviceName.setText(TextUtils.isEmpty(deviceName) ? "" : deviceName);
		holder.mTxtDeviceNameAddress.setText(TextUtils.isEmpty(deviceAddress) ? "" : deviceAddress);
//		holder.itemView.setOnClickListener(view -> {
//			if (TextUtils.isEmpty(deviceName) || TextUtils.isEmpty(deviceAddress))
//				return;
//			if (mListener != null)
//				mListener.onDeviceItemClick(view, deviceName, deviceAddress);
//		});
		holder.mBtnConnect.setOnClickListener(view -> {
			/* 接続ボタン押下 */
			if (mListener != null)
				mListener.onDeviceItemClick(view, deviceName, deviceAddress);
		});
		holder.mBtnBuoy.setOnClickListener(v -> {
			/* 浮標ボタン押下 */
		});
	}

	@Override
	public int getItemCount() {
		return mDeviceList.size();
	}

	public void addDevice(List<ScanResult> scanResults) {
		if (scanResults != null) {
			for (ScanResult scanResult : scanResults) {
				addDevice(scanResult, false);
			}
			notifyDataSetChanged();
		}
	}

	public void addDevice(ScanResult scanResult) {
		addDevice(scanResult, true);
	}

	public void addDevice(ScanResult scanResult, boolean notify) {
		if (scanResult == null)
			return;

		int existingPosition = getPosition(scanResult.getDevice().getAddress());

		if (existingPosition >= 0) {
			/* 追加済 更新する */
			mDeviceList.set(existingPosition, scanResult);
		}
		else {
			/* 新規追加 */
			mDeviceList.add(scanResult);
		}

		if (notify) {
			notifyDataSetChanged();
		}

	}

	private int getPosition(String address) {
		int position = -1;
		for (int i = 0; i < mDeviceList.size(); i++) {
			if (mDeviceList.get(i).getDevice().getAddress().equals(address)) {
				position = i;
				break;
			}
		}
		return position;
	}

	public void clearDevice() {
		mDeviceList.clear();
		notifyDataSetChanged();
	}
}
