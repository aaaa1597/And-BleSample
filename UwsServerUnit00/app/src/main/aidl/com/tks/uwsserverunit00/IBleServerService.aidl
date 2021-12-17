// IBleServerService.aidl
package com.tks.uwsserverunit00;
import com.tks.uwsserverunit00.IBleServerServiceCallback;
import com.tks.uwsserverunit00.DeviceInfo;

interface IBleServerService {
    void setCallback(IBleServerServiceCallback callback);	/* 常に後勝ち */
    int initBle();
    /* Scan */
    int startScan();
    int stopScan();
    List<DeviceInfo> getDeviceInfolist();
    DeviceInfo getDeviceInfo();
    void clearDevice();
    /* Communication */
    int connectDevice(String deviceAddress);
}
