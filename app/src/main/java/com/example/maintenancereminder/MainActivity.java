package com.example.maintenancereminder;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.view.View;

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
import com.example.maintenancereminder.ui.DeviceDetailActivity;
import com.example.maintenancereminder.ui.HistoryActivity;
import com.example.maintenancereminder.ui.SettingsActivity;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity {

    private EquipmentDao dao;
    private EquipmentAdapter adapter;
    private final ExecutorService ioExecutor = Executors.newSingleThreadExecutor();
    private final ActivityResultLauncher<String> notificationsPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), isGranted -> {});
    private final ActivityResultLauncher<Intent> addEquipmentLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() == RESULT_OK) {
                    loadData();
                }
            });

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        dao = new EquipmentDao(this);
        RecyclerView recyclerView = findViewById(R.id.recyclerViewEquipment);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        adapter = new EquipmentAdapter(new java.util.ArrayList<>(), item -> {
            Intent intent = new Intent(MainActivity.this, DeviceDetailActivity.class);
            intent.putExtra("equipment_id", item.id);
            startActivity(intent);
        });
        recyclerView.setAdapter(adapter);

        FloatingActionButton fab = findViewById(R.id.fabAdd);
        fab.setOnClickListener(view ->
                addEquipmentLauncher.launch(new Intent(MainActivity.this, AddEquipmentActivity.class)));
        findViewById(R.id.btnSettings).setOnClickListener(v -> startActivity(new Intent(this, SettingsActivity.class)));
        findViewById(R.id.btnJournal).setOnClickListener(v -> startActivity(new Intent(this, HistoryActivity.class)));

        requestNotificationPermissionIfNeeded();
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadData();
    }

    private void loadData() {
        ioExecutor.execute(() -> {
            List<Equipment> list = dao.getAllWithNearestDue();
            runOnUiThread(() -> {
                adapter.setItems(list);
                View empty = findViewById(R.id.tvEmptyState);
                empty.setVisibility(list.isEmpty() ? View.VISIBLE : View.GONE);
            });
        });
    }

    private void requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return;
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                == PackageManager.PERMISSION_GRANTED) return;
        notificationsPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS);
    }
}
