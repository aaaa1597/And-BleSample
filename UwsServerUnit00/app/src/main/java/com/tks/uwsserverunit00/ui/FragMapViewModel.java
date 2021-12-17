package com.tks.uwsserverunit00.ui;

import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

public class FragMapViewModel extends ViewModel {
	private MutableLiveData<Boolean> mPermission = new MutableLiveData<>(false);
	public MutableLiveData<Boolean> Permission() {
		return mPermission;
	}
}