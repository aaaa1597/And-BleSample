package com.tks.uwsserverunit00.ui;

import androidx.lifecycle.ViewModelProvider;
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
import com.tks.uwsserverunit00.R;
import com.tks.uwsserverunit00.TLog;

import static com.tks.uwsserverunit00.Constants.UWS_NG_ALREADY_SCANNED;
import static com.tks.uwsserverunit00.Constants.UWS_NG_BT_OFF;

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
		mViewModel.ScanStatus().observe(getViewLifecycleOwner(), status -> {
			int resid = (status == FragBleViewModel.BtnStatus.STARTSCAN) ? R.string.stopscan : R.string.startscan;
			((Button)getActivity().findViewById(R.id.btnScan)).setText(resid);
		});
		mViewModel.NotifyDataSetChanged().observe(getViewLifecycleOwner(), upd -> {
			TLog.d("Itemクリア DeviceListAdapter::notifyDataSetChanged()");
			mViewModel.getDeviceListAdapter().notifyDataSetChanged();
		});
		mViewModel.NotifyItemChanged().observe(getViewLifecycleOwner(), pos -> {
			TLog.d("Item変更 DeviceListAdapter::notifyItemChanged(pos＝{0})", pos);
			mViewModel.getDeviceListAdapter().notifyItemChanged(pos);
		});

		/* Scanボタン押下処理 */
		getActivity().findViewById(R.id.btnScan).setOnClickListener(v -> {
			Button btn = (Button)v;
			if(btn.getText().equals(/* Scan開始 */v.getResources().getString(R.string.startscan))) {
				TLog.d("Scan開始 btn.text={0}", btn.getText());
				int ret = mViewModel.startScan();
				if(ret == UWS_NG_ALREADY_SCANNED)	mViewModel.showSnacbar("既にscan中です。\n 続行します。");
				else if(ret == UWS_NG_BT_OFF)		mViewModel.showSnacbar("BluetoothがOFFです。\n ONにして下さい。");
			}
			else {
				TLog.d("Scan停止 btn.text={0}", btn.getText());
				mViewModel.stopScan();
			}
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