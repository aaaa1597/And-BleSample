package com.tks.uwsserverunit00.ui;

import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModelProvider;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.DividerItemDecoration;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;

import com.tks.uwsserverunit00.DeviceConnectActivity;
import com.tks.uwsserverunit00.MainActivity;
import com.tks.uwsserverunit00.R;

public class FragBle extends Fragment {
	private FragBleViewModel	mViewModel;
//	public static FragBle newInstance() {
//		return new FragBle();
//	}

	@Override
	public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
		return inflater.inflate(R.layout.frag_ble, container, false);
	}

	@Override
	public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
		super.onViewCreated(view, savedInstanceState);
		mViewModel = new ViewModelProvider(requireActivity()).get(FragBleViewModel.class);
		mViewModel.ScanBtnStr().observe(getViewLifecycleOwner(), str -> {
			((Button)getActivity().findViewById(R.id.btnScan)).setText(str);
		});
		mViewModel.NotifyDataSetChanged().observe(getViewLifecycleOwner(), upd -> {
			mViewModel.getDeviceListAdapter().notifyDataSetChanged();
		});
		mViewModel.NotifyItemChanged().observe(getViewLifecycleOwner(), pos -> {
			mViewModel.getDeviceListAdapter().notifyItemChanged(pos);
		});

		/* Scanボタン押下処理 */
		getActivity().findViewById(R.id.btnScan).setOnClickListener(v -> {
			Button btn = (Button)v;
			if(btn.getText().equals("scan開始"))
				mViewModel.startScan();
			else
				mViewModel.stopScan();
		});

		/* BLEデバイスリストの初期化 */
		RecyclerView deviceListRvw = getActivity().findViewById(R.id.rvw_devices);
		/* BLEデバイスリストに区切り線を表示 */
		DividerItemDecoration dividerItemDecoration = new DividerItemDecoration(deviceListRvw.getContext(), new LinearLayoutManager(getActivity().getApplicationContext()).getOrientation());
		deviceListRvw.addItemDecoration(dividerItemDecoration);
		deviceListRvw.setHasFixedSize(true);
		deviceListRvw.setLayoutManager(new LinearLayoutManager(getActivity().getApplicationContext()));
		deviceListRvw.setAdapter(mViewModel.getDeviceListAdapter());
	}
}