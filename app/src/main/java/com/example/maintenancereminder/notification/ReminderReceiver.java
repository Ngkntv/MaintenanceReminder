package com.example.maintenancereminder.notification;

import android.Manifest;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;

import com.example.maintenancereminder.R;
import com.example.maintenancereminder.ui.DeviceDetailActivity;

public class ReminderReceiver extends BroadcastReceiver {
    public static final String CHANNEL_ID = "maintenance_channel";

    @Override
    public void onReceive(Context context, Intent intent) {
        long deviceId = intent.getLongExtra("device_id", -1L);
        long taskId = intent.getLongExtra("task_id", -1L);
        String taskTitle = intent.getStringExtra("task_title");

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID, "Maintenance reminders", NotificationManager.IMPORTANCE_HIGH);
            NotificationManager nm = context.getSystemService(NotificationManager.class);
            if (nm != null) nm.createNotificationChannel(channel);
        }

        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            return;
        }

        Intent openIntent = new Intent(context, DeviceDetailActivity.class);
        if (deviceId > 0) openIntent.putExtra("equipment_id", deviceId);
        if (taskId > 0) openIntent.putExtra("highlight_task_id", taskId);
        PendingIntent contentIntent = PendingIntent.getActivity(
                context,
                taskId > 0 ? (int) taskId : 1,
                openIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        NotificationCompat.Builder b = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle("Напоминание о регламентной работе")
                .setContentText(taskTitle == null ? "Проверьте ближайшее обслуживание" : taskTitle)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .setContentIntent(contentIntent);

        NotificationManagerCompat.from(context).notify(taskId > 0 ? (int) taskId : 1, b.build());
    }
}
