package com.qinglian.fitness.admin;

import java.util.Locale;

final class DeviceSerialNumber {
    static final long MAX_SEQUENCE = 99_999L;
    private static final String MIDDLE_CODE = "K37A";
    private static final String TAIL_CODE = "X8M";

    private DeviceSerialNumber() {
    }

    static String format(String prefix, long sequence) {
        if (sequence < 1 || sequence > MAX_SEQUENCE) {
            throw new IllegalArgumentException("设备流水号超出 00001 至 99999 范围");
        }
        return prefix.trim().toUpperCase(Locale.ROOT)
            + MIDDLE_CODE
            + String.format(Locale.ROOT, "%05d", sequence)
            + TAIL_CODE;
    }
}
