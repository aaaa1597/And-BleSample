package com.test.blesample.central;

import java.util.UUID;

public class Constants {
	public static final int BLEMSG_1 = 1;
	public static final int BLEMSG_2 = 2;
	/* ServiceUUIDは0000xxxx-0000-1000-8000-00805f9b34fbの形を守る必要がある。CharacteristicUUIDはなんでもOK.*/
	public static final UUID UWS_SERVICE_UUID							= UUID.fromString("00002c2c-0000-1000-8000-00805f9b34fb");
	public static final UUID UWS_CHARACTERISTIC_SAMLE_UUID				= UUID.fromString("29292c2c-728c-4a2b-81cb-7b4d884adb04");

	/* 定義済UUIDに変換する "0000xxxx-0000-1000-8000-00805f9b34fb" */
	private static UUID convertFromInteger(int i) {
		final long MSB = 0x0000000000001000L;
		final long LSB = 0x800000805f9b34fbL;
		long value = i & 0xFFFFFFFF;
		return new UUID(MSB | (value << 32), LSB);
	}
}
