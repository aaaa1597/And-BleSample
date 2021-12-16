package com.tks.uwsserverunit00.ui;

import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.tks.uwsserverunit00.R;

public class FragBizLogic extends Fragment {
//	private FragBizLogicViewModel	mViewModel;

	public static FragBizLogic newInstance() {
		return new FragBizLogic();
	}

	@Override
	public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
		return inflater.inflate(R.layout.frag_bizlogic, container, false);
	}

	@Override
	public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
		super.onViewCreated(view, savedInstanceState);
//		mViewModel = new ViewModelProvider(requireActivity()).get(FragBizLogicViewModel.class);
//		mViewModel.clearDevice().observe(getViewLifecycleOwner(), new Observer() {
//			@Override
//			public void onChanged(Object o) {
//				mDeviceListAdapter.clearDevice();
//			}
//		});

		view.findViewById(R.id.btnSelectMember).setOnClickListener(v -> {
			DrawerLayout naviview = getActivity().findViewById(R.id.root_view);
			naviview.openDrawer(GravityCompat.START);
		});
	}
}