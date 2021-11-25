package com.test.blesample.central;

import java.util.UUID;

/**
 * Created by itanbarpeled on 28/01/2018.
 */

public class Constants {
    public static final int BLEMSG_1 = 1;
    public static final int BLEMSG_2 = 2;
    public static final UUID HEART_RATE_SERVICE_UUID = UUID.fromString("0000180D-0000-1000-8000-00805f9b34fb");
    public static final UUID BODY_SENSOR_LOCATION_CHARACTERISTIC_UUID = UUID.fromString("00002A38-0000-1000-8000-00805f9b34fb");

    private static UUID convertFromInteger(int i) {
        final long MSB = 0x0000000000001000L;
        final long LSB = 0x800000805f9b34fbL;
        long value = i & 0xFFFFFFFF;
        return new UUID(MSB | (value << 32), LSB);
    }
}
