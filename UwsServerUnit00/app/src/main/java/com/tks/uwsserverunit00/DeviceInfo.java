package com.tks.uwsserverunit00;

import android.os.Parcel;
import android.os.Parcelable;

public class DeviceInfo implements Parcelable {
	private final String	mDeviceName;
	private final String	mDeviceAddress;
	private final int		mDeviceRssi;
	private final boolean	mIsApplicable;
	private final int		mId;

	public DeviceInfo(String devicename, String deviceaddress, int devicerssi, boolean isApplicable, int id) {
		mDeviceName		= devicename;
		mDeviceAddress	= deviceaddress;
		mDeviceRssi		= devicerssi;
		mIsApplicable	= isApplicable;
		mId				= id;
	}

	protected DeviceInfo(Parcel in) {
		mDeviceName		= in.readString();
		mDeviceAddress	= in.readString();
		mDeviceRssi		= in.readInt();
		mIsApplicable	= in.readByte() != 0x00;
		mId				= in.readInt();
	}

	public static final Creator<DeviceInfo> CREATOR = new Creator<DeviceInfo>() {
		@Override
		public DeviceInfo createFromParcel(Parcel in) {
			return new DeviceInfo(in);
		}

		@Override
		public DeviceInfo[] newArray(int size) {
			return new DeviceInfo[size];
		}
	};

	@Override
	public int describeContents() {
		return 0;
	}

	@Override
	public void writeToParcel(Parcel dest, int flags) {
		dest.writeString(mDeviceName);
		dest.writeString(mDeviceAddress);
		dest.writeInt(mDeviceRssi);
		dest.writeByte((byte)(mIsApplicable?0x01:0x00));
		dest.writeInt(mId);
	}

	public String	getDeviceName()		{ return mDeviceName;}
	public String	getDeviceAddress()	{ return mDeviceAddress;}
	public int		getDeviceRssi()		{ return mDeviceRssi;}
	public boolean	isApplicable()		{ return mIsApplicable;}
	public int		getId()				{ return mId;}
}
