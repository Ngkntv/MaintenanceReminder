package com.example.maintenancereminder.notification;

import android.Manifest;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.util.Log;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;

import com.example.maintenancereminder.R;

public class ReminderReceiver extends BroadcastReceiver {
    public static final String CHANNEL_ID = "maintenance_channel";

    @Override
    public void onReceive(Context context, Intent intent) {
        try {
            String name = intent.getStringExtra("equipment_name");
            int id = intent.getIntExtra("equipment_id", 1);

            Log.d("REMINDER", "Receiver fired. id=" + id + ", name=" + name);

            // 1) Канал (Android 8+)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                NotificationChannel channel = new NotificationChannel(
                        CHANNEL_ID,
                        "ТО оборудования",
                        NotificationManager.IMPORTANCE_HIGH
                );
                NotificationManager nm = context.getSystemService(NotificationManager.class);
                if (nm != null) nm.createNotificationChannel(channel);
            }

            // 2) Проверка permission на Android 13+
            if (Build.VERSION.SDK_INT >= 33) {
                if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
                        != PackageManager.PERMISSION_GRANTED) {
                    Log.w("REMINDER", "POST_NOTIFICATIONS not granted, skip notify");
                    return;
                }
            }

            // 3) Нотификация
            NotificationCompat.Builder b = new NotificationCompat.Builder(context, CHANNEL_ID)
                    .setSmallIcon(R.mipmap.ic_launcher)
                    .setContentTitle("Напоминание о ТО")
                    .setContentText("Нужно выполнить ТО: " + (name == null ? "оборудование" : name))
                    .setPriority(NotificationCompat.PRIORITY_HIGH)
                    .setAutoCancel(true);

            NotificationManagerCompat.from(context).notify(id, b.build());
            Log.d("REMINDER", "Notification shown. id=" + id);

        } catch (Exception e) {
            Log.e("REMINDER", "Receiver error", e);
        }
    }
}
