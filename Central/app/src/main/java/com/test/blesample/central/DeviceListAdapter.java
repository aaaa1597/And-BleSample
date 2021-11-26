package com.test.blesample.central;

import android.bluetooth.le.ScanResult;
import android.support.v7.widget.RecyclerView;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by itanbarpeled on 28/01/2018.
 */

public class DeviceListAdapter extends RecyclerView.Adapter<DeviceListAdapter.ViewHolder> {
	/* インターフェース : DeviceListAdapterListener */
	public interface DeviceListAdapterListener {
		void onDeviceItemClick(String deviceName, String deviceAddress);
	}

	static class ViewHolder extends RecyclerView.ViewHolder {
		TextView mTxtDeviceName;
		TextView mTxtDeviceNameAddress;
		ViewHolder(View view) {
			super(view);
			mTxtDeviceName			= view.findViewById(R.id.device_name);
			mTxtDeviceNameAddress	= view.findViewById(R.id.device_address);
		}
	}

	/* メンバ変数 */
	private ArrayList<ScanResult>		mDeviceList = new ArrayList<ScanResult>();
	private DeviceListAdapterListener	mListener;

	public DeviceListAdapter(DeviceListAdapterListener listener) {
		mListener = listener;
	}

	public DeviceListAdapter.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
		View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.lay_listitem_devices, parent, false);
		return new ViewHolder(view);
	}

	@Override
	public void onBindViewHolder(ViewHolder holder, int position) {
		ScanResult scanResult = mDeviceList.get(position);
		final String deviceName		= scanResult.getDevice().getName();
		final String deviceAddress	= scanResult.getDevice().getAddress();

        holder.mTxtDeviceName.setText(TextUtils.isEmpty(deviceName) ? "" : deviceName);
        holder.mTxtDeviceNameAddress.setText(TextUtils.isEmpty(deviceAddress) ? "" : deviceAddress);
		holder.itemView.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View view) {
                if (TextUtils.isEmpty(deviceName) || TextUtils.isEmpty(deviceAddress))
                    return;

                if (mListener != null)
                    mListener.onDeviceItemClick(deviceName, deviceAddress);
			}
		});
	}

	@Override
	public int getItemCount() {
		return mDeviceList.size();
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

	public void addDevice(List<ScanResult> scanResults) {
		if (scanResults != null) {
			for (ScanResult scanResult : scanResults) {
				addDevice(scanResult, false);
			}
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
