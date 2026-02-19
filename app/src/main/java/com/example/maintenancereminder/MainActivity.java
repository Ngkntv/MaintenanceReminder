package com.example.maintenancereminder;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import com.example.maintenancereminder.db.EquipmentDao;
import com.example.maintenancereminder.model.Equipment;
import com.example.maintenancereminder.notification.ReminderScheduler;
import com.example.maintenancereminder.ui.AddEquipmentActivity;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class MainActivity extends AppCompatActivity {

    private EquipmentDao dao;
    private ListView listView;
    private List<Equipment> currentItems = new ArrayList<>();

    private static final String CHANNEL_ID = "maintenance_channel";
    private static final int REQ_NOTI = 1001;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            android.app.AlarmManager am = (android.app.AlarmManager) getSystemService(android.content.Context.ALARM_SERVICE);
            if (am != null && !am.canScheduleExactAlarms()) {
                android.content.Intent intent = new android.content.Intent(android.provider.Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM);
                startActivity(intent);
            }
        }

        dao = new EquipmentDao(this);
        listView = findViewById(R.id.listViewEquipment);
        Button btnAdd = findViewById(R.id.btnAdd);
        Button btnTest = findViewById(R.id.btnTestNotification);

        requestNotificationPermissionIfNeeded();

        btnTest.setOnClickListener(v -> showTestNotificationNow());

        btnAdd.setOnClickListener(v ->
                startActivity(new Intent(this, AddEquipmentActivity.class))
        );

        // Обычный клик: редактирование
        listView.setOnItemClickListener((parent, view, position, id) -> {
            if (position < 0 || position >= currentItems.size()) return;
            Equipment selected = currentItems.get(position);
            Intent intent = new Intent(MainActivity.this, AddEquipmentActivity.class);
            intent.putExtra("equipment_id", selected.id);
            startActivity(intent);
        });

        // Долгий клик: "ТО выполнено"
        listView.setOnItemLongClickListener((parent, view, position, id) -> {
            try {
                if (currentItems.isEmpty()) {
                    Toast.makeText(this, "Список пуст", Toast.LENGTH_SHORT).show();
                    return true;
                }
                if (position < 0 || position >= currentItems.size()) {
                    Toast.makeText(this, "Неверная позиция: " + position, Toast.LENGTH_SHORT).show();
                    return true;
                }

                Equipment e = currentItems.get(position);
                if (e == null) {
                    Toast.makeText(this, "Элемент не найден", Toast.LENGTH_SHORT).show();
                    return true;
                }

                // Режим A: ТО выполнено сейчас
                e.lastServiceDate = System.currentTimeMillis();
                e.nextServiceDate = e.lastServiceDate + e.serviceIntervalDays * 24L * 60L * 60L * 1000L;

                dao.update(e);

                // Если scheduler иногда падает по SecurityException, не роняем приложение
                try {
                    ReminderScheduler.schedule(this, e);
                } catch (Exception ignored) {
                    Log.w("REMINDER", "schedule failed on long tap");
                }

                Toast.makeText(this, "ТО обновлено: " + e.name, Toast.LENGTH_SHORT).show();
                loadData();

            } catch (Exception ex) {
                Log.e("LONG_TAP_ERROR", "Ошибка long tap", ex);
                Toast.makeText(this, "Ошибка: " + ex.getClass().getSimpleName(), Toast.LENGTH_LONG).show();
            }
            return true;
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadData();
    }

    private void loadData() {
        currentItems = dao.getAll(); // один запрос в БД
        List<String> lines = new ArrayList<>();
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.US);

        for (Equipment e : currentItems) {
            String line = e.name
                    + " | barcode: " + e.barcode
                    + " | last: " + sdf.format(e.lastServiceDate)
                    + " | next: " + sdf.format(e.nextServiceDate);
            lines.add(line);
        }

        listView.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, lines));
    }

    private void requestNotificationPermissionIfNeeded() {
        if (android.os.Build.VERSION.SDK_INT >= 33) {
            if (checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)
                    != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{android.Manifest.permission.POST_NOTIFICATIONS}, REQ_NOTI);
            }
        }
    }

    private void showTestNotificationNow() {
        try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                android.app.NotificationChannel channel = new android.app.NotificationChannel(
                        CHANNEL_ID,
                        "ТО оборудования",
                        android.app.NotificationManager.IMPORTANCE_HIGH
                );
                android.app.NotificationManager nm = getSystemService(android.app.NotificationManager.class);
                if (nm != null) nm.createNotificationChannel(channel);
            }

            if (android.os.Build.VERSION.SDK_INT >= 33 &&
                    checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)
                            != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "Нет разрешения на уведомления", Toast.LENGTH_SHORT).show();
                return;
            }

            NotificationCompat.Builder b = new NotificationCompat.Builder(this, CHANNEL_ID)
                    .setSmallIcon(R.mipmap.ic_launcher)
                    .setContentTitle("Тест")
                    .setContentText("Уведомления работают")
                    .setPriority(NotificationCompat.PRIORITY_HIGH)
                    .setAutoCancel(true);

            NotificationManagerCompat.from(this).notify(999, b.build());
            Toast.makeText(this, "Тест отправлен", Toast.LENGTH_SHORT).show();

        } catch (Exception e) {
            Log.e("NOTI_TEST", "showTestNotificationNow error", e);
            Toast.makeText(this, "Ошибка: " + e.getClass().getSimpleName(), Toast.LENGTH_LONG).show();
        }
    }
}
