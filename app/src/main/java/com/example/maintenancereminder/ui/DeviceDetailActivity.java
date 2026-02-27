package com.example.maintenancereminder.ui;

import android.content.Intent;
import android.os.Bundle;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.maintenancereminder.R;
import com.example.maintenancereminder.db.EquipmentDao;
import com.example.maintenancereminder.db.MaintenanceRepository;
import com.example.maintenancereminder.db.MaintenanceTaskDao;
import com.example.maintenancereminder.model.Equipment;
import com.example.maintenancereminder.model.MaintenanceTask;

public class DeviceDetailActivity extends AppCompatActivity {
    private long deviceId;
    private EquipmentDao equipmentDao;
    private MaintenanceTaskDao taskDao;
    private MaintenanceRepository repository;
    private TaskAdapter adapter;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_device_detail);

        deviceId = getIntent().getLongExtra("equipment_id", -1L);
        equipmentDao = new EquipmentDao(this);
        taskDao = new MaintenanceTaskDao(this);
        repository = new MaintenanceRepository(this);

        adapter = new TaskAdapter(task -> {
            Intent intent = new Intent(this, TaskEditActivity.class);
            intent.putExtra("device_id", deviceId);
            intent.putExtra("task_id", task.id);
            startActivity(intent);
        });
        RecyclerView rv = findViewById(R.id.recyclerViewTasks);
        rv.setLayoutManager(new LinearLayoutManager(this));
        rv.setAdapter(adapter);

        findViewById(R.id.btnAddTask).setOnClickListener(v -> {
            Intent intent = new Intent(this, TaskEditActivity.class);
            intent.putExtra("device_id", deviceId);
            startActivity(intent);
        });

        findViewById(R.id.btnEditDevice).setOnClickListener(v -> {
            Intent intent = new Intent(this, AddEquipmentActivity.class);
            intent.putExtra("equipment_id", deviceId);
            startActivity(intent);
        });

        findViewById(R.id.btnDeviceHistory).setOnClickListener(v -> {
            Intent intent = new Intent(this, HistoryActivity.class);
            intent.putExtra("device_id", deviceId);
            startActivity(intent);
        });

        findViewById(R.id.btnDeleteDevice).setOnClickListener(v -> new AlertDialog.Builder(this)
                .setTitle("Удалить устройство?")
                .setMessage("Будут удалены задачи, история и напоминания")
                .setPositiveButton("Удалить", (d, w) -> {
                    repository.deleteDevice(this, deviceId);
                    finish();
                }).setNegativeButton("Отмена", null).show());
    }

    @Override protected void onResume() {
        super.onResume();
        Equipment e = equipmentDao.getById(deviceId);
        if (e == null) { finish(); return; }
        ((TextView) findViewById(R.id.tvDeviceTitle)).setText(e.name);
        ((TextView) findViewById(R.id.tvDeviceMeta)).setText((e.category == null ? "" : e.category) + "\n" + (e.notes == null ? "" : e.notes));
        java.util.List<MaintenanceTask> tasks = taskDao.getByDevice(deviceId);
        adapter.submit(tasks);
        if (tasks.isEmpty()) Toast.makeText(this, "Нет регламентных работ", Toast.LENGTH_SHORT).show();
    }
}
