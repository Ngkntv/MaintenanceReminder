package com.example.maintenancereminder.notification;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.Log;

import com.example.maintenancereminder.db.MaintenanceTaskDao;
import com.example.maintenancereminder.db.SettingsDao;
import com.example.maintenancereminder.model.AppSettings;
import com.example.maintenancereminder.model.MaintenanceTask;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

public class ReminderScheduler {
    private static final String TAG = "ReminderScheduler";

    private static final long SCHEDULE_PAST_GRACE_MS = 5_000L;

    public static boolean scheduleTaskReminder(Context context, MaintenanceTask task) {
        if (context == null) {
            Log.w(TAG, "scheduleTaskReminder skipped: context is null");
            return false;
        }
        if (task == null) {
            Log.w(TAG, "scheduleTaskReminder skipped: task is null");
            return false;
        }
        if (task.id == null) {
            Log.w(TAG, "scheduleTaskReminder skipped: task.id is null");
            return false;
        }
        if (task.nextDueDate == null) {
            Log.w(TAG, "scheduleTaskReminder skipped: task.nextDueDate is null for taskId=" + task.id);
            return false;
        }
        if (task.deviceId == null) {
            Log.w(TAG, "scheduleTaskReminder skipped: task.deviceId is null for taskId=" + task.id);
            return false;
        }

        Context appContext = context.getApplicationContext();
        AppSettings settings = new SettingsDao(appContext).getSettings();
        LocalDate dueDate = Instant.ofEpochMilli(task.nextDueDate).atZone(ZoneId.systemDefault()).toLocalDate();
        long trigger = dueDate.atTime(settings.notificationHour, 0)
                .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();

        long now = System.currentTimeMillis();
        if (trigger <= now) {
            trigger = now + SCHEDULE_PAST_GRACE_MS;
        }

        Log.d(TAG, "Scheduling reminder: taskId=" + task.id
                + ", deviceId=" + task.deviceId
                + ", nextDueDate=" + task.nextDueDate
                + ", triggerMillis=" + trigger);

        AlarmManager am = (AlarmManager) appContext.getSystemService(Context.ALARM_SERVICE);
        if (am == null) {
            Log.e(TAG, "scheduleTaskReminder failed: AlarmManager is null for taskId=" + task.id);
            return false;
        }

        PendingIntent pi = buildPendingIntent(appContext, task.id.intValue(), task.deviceId, task.id, task.title);

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (am.canScheduleExactAlarms()) {
                    am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, trigger, pi);
                    Log.d(TAG, "Exact alarm scheduled on API 31+");
                    return true;
                }

                Log.w(TAG, "Exact alarms are not allowed; scheduling inexact fallback for taskId=" + task.id);
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, trigger, pi);
                return true;
            }

            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, trigger, pi);
            Log.d(TAG, "Exact alarm scheduled on API <31");
            return true;
        } catch (Exception exactException) {
            Log.e(TAG, "Failed to schedule exact reminder for taskId=" + task.id, exactException);
            try {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, trigger, pi);
                Log.w(TAG, "Fallback alarm scheduled after exact scheduling failure for taskId=" + task.id);
                return true;
            } catch (Exception fallbackException) {
                Log.e(TAG, "Failed to schedule fallback reminder for taskId=" + task.id, fallbackException);
                return false;
            }
        }
    }


    public static boolean isReminderScheduled(Context context, long taskId) {
        Context appContext = context.getApplicationContext();
        PendingIntent pi = PendingIntent.getBroadcast(
                appContext,
                (int) taskId,
                new Intent(appContext, ReminderReceiver.class),
                PendingIntent.FLAG_NO_CREATE | PendingIntent.FLAG_IMMUTABLE
        );
        return pi != null;
    }

    public static long getReminderTriggerTime(Context context, MaintenanceTask task) {
        if (task == null || task.nextDueDate == null) {
            return -1L;
        }
        AppSettings settings = new SettingsDao(context.getApplicationContext()).getSettings();
        LocalDate dueDate = Instant.ofEpochMilli(task.nextDueDate).atZone(ZoneId.systemDefault()).toLocalDate();
        return dueDate.atTime(settings.notificationHour, 0)
                .atZone(ZoneId.systemDefault())
                .toInstant()
                .toEpochMilli();
    }


    public static boolean scheduleTestReminderAfter5Seconds(Context context, MaintenanceTask task) {
        if (context == null || task == null || task.id == null) {
            return false;
        }
        Context appContext = context.getApplicationContext();
        AlarmManager am = (AlarmManager) appContext.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return false;

        long trigger = System.currentTimeMillis() + 5_000L;
        int requestCode = 1_000_000 + task.id.intValue();
        PendingIntent pi = buildPendingIntent(appContext, requestCode, task.deviceId, task.id, "тестовое напоминание");
        try {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, trigger, pi);
            return true;
        } catch (Exception e) {
            try {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, trigger, pi);
                return true;
            } catch (Exception ignored) {
                return false;
            }
        }
    }

    public static void cancelTaskReminder(Context context, long taskId) {
        Context appContext = context.getApplicationContext();
        AlarmManager am = (AlarmManager) appContext.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;
        PendingIntent pi = buildPendingIntent(appContext, (int) taskId, -1L, taskId, null);
        am.cancel(pi);
    }

    public static void rescheduleAll(Context context) {
        MaintenanceTaskDao dao = new MaintenanceTaskDao(context);
        for (MaintenanceTask task : dao.getAllActive()) {
            scheduleTaskReminder(context, task);
        }
    }

    private static PendingIntent buildPendingIntent(Context context, int requestCode, Long deviceId, Long taskId, String taskTitle) {
        Intent i = new Intent(context, ReminderReceiver.class);
        i.putExtra("task_id", taskId == null ? -1L : taskId);
        i.putExtra("device_id", deviceId == null ? -1L : deviceId);
        i.putExtra("task_title", taskTitle);
        return PendingIntent.getBroadcast(context, requestCode, i,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }
}
