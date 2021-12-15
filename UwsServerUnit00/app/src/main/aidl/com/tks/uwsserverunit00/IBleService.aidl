// IBleService.aidl
package com.tks.uwsserverunit00;
import com.tks.uwsserverunit00.IBleServiceCallback;
import com.tks.uwsserverunit00.DeviceInfo;

interface IBleService {
    void setCallback(IBleServiceCallback callback);	/* 常に後勝ち */
    int initBle();
    int startScan();
    int stopScan();
    List<DeviceInfo> getDeviceInfolist();
    DeviceInfo getDeviceInfo();
    int connectDevice(String deviceAddress);
    void clearDevice();
}
