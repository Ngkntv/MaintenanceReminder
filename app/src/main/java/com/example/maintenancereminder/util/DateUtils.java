package com.example.maintenancereminder.util;

public class DateUtils {
    public static long calcNextServiceDate(long lastServiceMillis, long intervalDays) {
        return lastServiceMillis + intervalDays * 24L * 60L * 60L * 1000L;
    }
}
