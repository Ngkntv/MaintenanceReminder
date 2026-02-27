package com.example.maintenancereminder.notification;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
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

    public static void scheduleTaskReminder(Context context, MaintenanceTask task) {
        if (context == null || task == null || task.id == null || task.nextDueDate == null) return;
        try {
            AppSettings settings = new SettingsDao(context).getSettings();
            LocalDate dueDate = Instant.ofEpochMilli(task.nextDueDate).atZone(ZoneId.systemDefault()).toLocalDate();
            long trigger = dueDate.atTime(settings.notificationHour, 0)
                    .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();

            AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
            if (am == null) return;

            PendingIntent pi = buildPendingIntent(context, task.id.intValue(), task.deviceId, task.id, task.title);
            if (trigger <= System.currentTimeMillis()) {
                trigger = System.currentTimeMillis() + 5_000L;
            }
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, trigger, pi);
        } catch (SecurityException se) {
            Log.e(TAG, "No permission for exact alarm", se);
        } catch (Exception e) {
            Log.e(TAG, "Failed to schedule reminder", e);
        }
    }

    public static void cancelTaskReminder(Context context, long taskId) {
        try {
            AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
            if (am == null) return;
            PendingIntent pi = buildPendingIntent(context, (int) taskId, -1L, taskId, null);
            am.cancel(pi);
        } catch (Exception e) {
            Log.e(TAG, "Failed to cancel reminder for taskId=" + taskId, e);
        }
    }

    public static void rescheduleAll(Context context) {
        MaintenanceTaskDao dao = new MaintenanceTaskDao(context);
        for (MaintenanceTask task : dao.getAllActive()) {
            scheduleTaskReminder(context, task);
        }
    }

    private static PendingIntent buildPendingIntent(Context context, int requestCode, Long deviceId, Long taskId, String taskTitle) {
        Intent i = new Intent(context, ReminderReceiver.class);
        i.putExtra("task_id", taskId);
        i.putExtra("device_id", deviceId);
        i.putExtra("task_title", taskTitle);
        return PendingIntent.getBroadcast(context, requestCode, i,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }
}
