// IBleServerServiceCallback.aidl
package com.tks.uwsserverunit00;

interface IBleServerServiceCallback {
    void notifyGattConnected(String Address);
    void notifyGattDisConnected(String Address);
    void notifyDeviceInfolist();
    void notifyDeviceInfo();
    void notifyScanEnd();
    void notifyServicesDiscovered(String Address, int status);
    void notifyApplicable(String Address, boolean status);
    void notifyReady2DeviceCommunication(String Address, boolean status);
    void notifyResRead(String Address, long ldatetime, double longitude, double latitude, int heartbeat, int status);
    void notifyFromPeripheral(String Address, long ldatetime, double longitude, double latitude, int heartbeat);
    void notifyError(int errcode, String errmsg);
}
