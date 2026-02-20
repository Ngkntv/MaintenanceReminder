package com.example.maintenancereminder;

import android.Manifest;
import android.app.AlarmManager;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Build;
import android.provider.Settings;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.example.maintenancereminder.db.EquipmentDao;
import com.example.maintenancereminder.model.Equipment;
import com.example.maintenancereminder.ui.AddEquipmentActivity;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Главная активность отображает список оборудования в виде RecyclerView. При нажатии на
 * карточку можно перейти в режим редактирования, а при нажатии на плавающую кнопку
 * добавляется новая запись.
 */
public class MainActivity extends AppCompatActivity {

    private EquipmentDao dao;
    private RecyclerView recyclerView;
    private EquipmentAdapter adapter;
    private final ExecutorService ioExecutor = Executors.newSingleThreadExecutor();
    private final ActivityResultLauncher<String> notificationsPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), isGranted -> {
                // Разрешение необязательное, но без него нотификации на Android 13+ не покажутся.
            });

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        dao = new EquipmentDao(this);
        recyclerView = findViewById(R.id.recyclerViewEquipment);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setHasFixedSize(true);

        adapter = new EquipmentAdapter(new java.util.ArrayList<>(), item -> {
            // Обычный клик — открываем запись для редактирования
            Intent intent = new Intent(MainActivity.this, AddEquipmentActivity.class);
            intent.putExtra("equipment_id", item.id);
            startActivity(intent);
        }, item -> {
            // Долгий клик — ТО выполнено. Обновляем даты и перезаписываем.
            long now = System.currentTimeMillis();
            item.lastServiceDate = now;
            item.nextServiceDate = now + item.serviceIntervalDays * 24L * 60L * 60L * 1000L;
            ioExecutor.execute(() -> {
                dao.update(item);
                loadData();
            });
        });
        recyclerView.setAdapter(adapter);

        FloatingActionButton fab = findViewById(R.id.fabAdd);
        fab.setOnClickListener(view -> startActivity(new Intent(MainActivity.this, AddEquipmentActivity.class)));

        requestNotificationPermissionIfNeeded();
        requestExactAlarmPermissionIfNeeded();
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadData();
    }

    /**
     * Загружает данные из базы и обновляет адаптер.
     */
    private void loadData() {
        ioExecutor.execute(() -> {
            List<Equipment> list = dao.getAll();
            runOnUiThread(() -> {
                if (!isFinishing() && !isDestroyed()) {
                    adapter.setItems(list);
                }
            });
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        ioExecutor.shutdownNow();
    }

    private void requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return;
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                == PackageManager.PERMISSION_GRANTED) {
            return;
        }
        notificationsPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS);
    }

    private void requestExactAlarmPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return;
        AlarmManager alarmManager = getSystemService(AlarmManager.class);
        if (alarmManager != null && alarmManager.canScheduleExactAlarms()) return;

        Intent intent = new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM);
        intent.setData(Uri.parse("package:" + getPackageName()));
        startActivity(intent);
    }
}
