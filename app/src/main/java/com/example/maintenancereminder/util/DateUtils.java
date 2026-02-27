package com.example.maintenancereminder.util;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

public class DateUtils {
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    public static long calculateNextDueDate(long baseMillis, long intervalValue, String unit) {
        LocalDate baseDate = Instant.ofEpochMilli(baseMillis).atZone(ZoneId.systemDefault()).toLocalDate();
        return calculateNextDueDate(baseDate, intervalValue, unit)
                .atStartOfDay(ZoneId.systemDefault())
                .toInstant()
                .toEpochMilli();
    }

    public static LocalDate calculateNextDueDate(LocalDate baseDate, long intervalValue, String unit) {
        LocalDate safeBaseDate = baseDate == null ? LocalDate.now() : baseDate;
        long safeInterval = Math.max(1, intervalValue);
        String safeUnit = unit == null ? "DAYS" : unit;
        LocalDate next;
        switch (safeUnit) {
            case "WEEKS":
                next = safeBaseDate.plusWeeks(safeInterval);
                break;
            case "MONTHS":
                next = safeBaseDate.plusMonths(safeInterval);
                break;
            case "YEARS":
                next = safeBaseDate.plusYears(safeInterval);
                break;
            case "DAYS":
            default:
                next = safeBaseDate.plusDays(safeInterval);
                break;
        }
        return next;
    }

    public static String formatDate(long millis) {
        return Instant.ofEpochMilli(millis)
                .atZone(ZoneId.systemDefault())
                .toLocalDate()
                .format(FORMATTER);
    }
}
