package com.example.maintenancereminder.util;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

public class DateUtils {
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    public static long calculateNextDueDate(long baseMillis, long intervalValue, String unit) {
        LocalDate baseDate = Instant.ofEpochMilli(baseMillis).atZone(ZoneId.systemDefault()).toLocalDate();
        LocalDate next;
        switch (unit) {
            case "WEEKS":
                next = baseDate.plusWeeks(intervalValue);
                break;
            case "MONTHS":
                next = baseDate.plusMonths(intervalValue);
                break;
            case "YEARS":
                next = baseDate.plusYears(intervalValue);
                break;
            case "DAYS":
            default:
                next = baseDate.plusDays(intervalValue);
                break;
        }
        return next.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli();
    }

    public static String formatDate(long millis) {
        return Instant.ofEpochMilli(millis)
                .atZone(ZoneId.systemDefault())
                .toLocalDate()
                .format(FORMATTER);
    }
}
