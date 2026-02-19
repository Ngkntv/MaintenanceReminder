package com.example.maintenancereminder.notification;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.Log;

import com.example.maintenancereminder.model.Equipment;

public class ReminderScheduler {

    public static void schedule(Context context, Equipment e) {
        if (context == null || e == null) return;
        scheduleAt(context, e, e.nextServiceDate, safeIntId(e.id));
    }

    public static void scheduleIn10Sec(Context context, Equipment e) {
        if (context == null || e == null) return;
        long trigger = System.currentTimeMillis() + 10_000L;
        scheduleAt(context, e, trigger, safeIntId(e.id) + 500000);
    }

    private static void scheduleAt(Context context, Equipment e, long triggerAtMillis, int requestCode) {
        try {
            AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
            if (am == null) return;

            long now = System.currentTimeMillis();
            long trigger = triggerAtMillis;
            if (trigger <= now) trigger = now + 10_000L; // защита от прошлого времени

            Intent i = new Intent(context, ReminderReceiver.class);
            i.putExtra("equipment_name", e.name);
            i.putExtra("equipment_id", requestCode);

            PendingIntent pi = PendingIntent.getBroadcast(
                    context,
                    requestCode,
                    i,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
            );

            Log.d("REMINDER", "scheduleAt(): req=" + requestCode + " trigger=" + trigger + " now=" + now);

            // Для теста и демо на API 35 нужно EXACT, иначе система может сдвинуть
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, trigger, pi);
            } else {
                am.setExact(AlarmManager.RTC_WAKEUP, trigger, pi);
            }

        } catch (SecurityException se) {
            Log.e("REMINDER", "Exact alarms not allowed / SecurityException", se);
        } catch (Exception ex) {
            Log.e("REMINDER", "scheduleAt error", ex);
        }
    }

    private static int safeIntId(long id) {
        if (id < 1) return 1;
        if (id > Integer.MAX_VALUE) return (int) (id % Integer.MAX_VALUE);
        return (int) id;
    }
}
