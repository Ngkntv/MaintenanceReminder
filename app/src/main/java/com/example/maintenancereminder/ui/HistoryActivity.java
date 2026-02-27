package com.example.maintenancereminder.ui;

import android.os.Bundle;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.maintenancereminder.R;
import com.example.maintenancereminder.db.HistoryDao;

public class HistoryActivity extends AppCompatActivity {
    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_history);

        long deviceId = getIntent().getLongExtra("device_id", -1L);
        HistoryDao dao = new HistoryDao(this);
        HistoryAdapter adapter = new HistoryAdapter(entry -> new AlertDialog.Builder(this)
                .setMessage("Удалить запись истории?")
                .setPositiveButton("Удалить", (d, w) -> { dao.deleteById(entry.id); recreate(); })
                .setNegativeButton("Отмена", null)
                .show());

        RecyclerView rv = findViewById(R.id.recyclerViewHistory);
        rv.setLayoutManager(new LinearLayoutManager(this));
        rv.setAdapter(adapter);

        if (deviceId > 0) {
            setTitle("История устройства");
            adapter.submit(dao.getByDevice(deviceId));
        } else {
            setTitle("Общий журнал");
            adapter.submit(dao.getAll());
        }
    }
}
