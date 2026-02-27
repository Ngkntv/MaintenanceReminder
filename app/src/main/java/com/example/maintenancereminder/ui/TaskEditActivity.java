package com.example.maintenancereminder.ui;

import android.os.Bundle;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.example.maintenancereminder.R;
import com.example.maintenancereminder.db.MaintenanceRepository;
import com.example.maintenancereminder.db.MaintenanceTaskDao;
import com.example.maintenancereminder.model.MaintenanceTask;
import com.example.maintenancereminder.notification.ReminderScheduler;

import java.time.LocalDate;
import java.time.ZoneId;

public class TaskEditActivity extends AppCompatActivity {
    private long deviceId;
    private long taskId;
    private MaintenanceTaskDao dao;
    private MaintenanceRepository repository;
    private MaintenanceTask editing;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_task_edit);
        dao = new MaintenanceTaskDao(this);
        repository = new MaintenanceRepository(this);

        deviceId = getIntent().getLongExtra("device_id", -1L);
        taskId = getIntent().getLongExtra("task_id", -1L);

        Spinner spUnit = findViewById(R.id.spIntervalUnit);
        Spinner spPriority = findViewById(R.id.spPriority);
        spUnit.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, new String[]{"DAYS", "WEEKS", "MONTHS", "YEARS"}));
        spPriority.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, new String[]{"LOW", "MEDIUM", "HIGH"}));

        if (taskId != -1L) {
            editing = dao.getById(taskId);
            if (editing != null) {
                ((EditText) findViewById(R.id.etTaskTitle)).setText(editing.title);
                ((EditText) findViewById(R.id.etIntervalValue)).setText(String.valueOf(editing.intervalValue));
                ((EditText) findViewById(R.id.etNextDueDate)).setText(com.example.maintenancereminder.util.DateUtils.formatDate(editing.nextDueDate));
                ((EditText) findViewById(R.id.etTaskComment)).setText(editing.comment);
                ((EditText) findViewById(R.id.etCost)).setText(editing.cost == null ? "" : String.valueOf(editing.cost));
                ((EditText) findViewById(R.id.etConsumables)).setText(editing.consumables);
                setSpinnerSelection(spUnit, editing.intervalUnit);
                setSpinnerSelection(spPriority, editing.priority);
            }
        }

        Button btnComplete = findViewById(R.id.btnCompleteTask);
        btnComplete.setOnClickListener(v -> completeTask());
        btnComplete.setEnabled(taskId != -1L);

        findViewById(R.id.btnRollback).setOnClickListener(v -> {
            if (taskId != -1L && repository.rollbackLastCompletion(this, taskId)) {
                Toast.makeText(this, "Последнее выполнение отменено", Toast.LENGTH_SHORT).show();
            }
        });

        findViewById(R.id.btnDeleteTask).setOnClickListener(v -> {
            if (taskId == -1L) return;
            new AlertDialog.Builder(this).setMessage("Удалить задачу?")
                    .setPositiveButton("Удалить", (d,w) -> {
                        repository.deleteTask(this, taskId);
                        finish();
                    }).setNegativeButton("Отмена", null).show();
        });

        findViewById(R.id.btnSaveTask).setOnClickListener(v -> saveTask());
    }

    private void saveTask() {
        String title = ((EditText) findViewById(R.id.etTaskTitle)).getText().toString().trim();
        String intervalStr = ((EditText) findViewById(R.id.etIntervalValue)).getText().toString().trim();
        String dateStr = ((EditText) findViewById(R.id.etNextDueDate)).getText().toString().trim();
        if (title.isEmpty() || intervalStr.isEmpty() || dateStr.isEmpty()) {
            Toast.makeText(this, "Заполните обязательные поля", Toast.LENGTH_SHORT).show();
            return;
        }

        long interval;
        long dueMillis;
        try {
            interval = Long.parseLong(intervalStr);
            if (interval <= 0) throw new IllegalArgumentException();
            dueMillis = LocalDate.parse(dateStr).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli();
        } catch (Exception e) {
            Toast.makeText(this, "Проверьте формат даты yyyy-MM-dd и период", Toast.LENGTH_SHORT).show();
            return;
        }

        MaintenanceTask task = editing == null ? new MaintenanceTask() : editing;
        task.deviceId = deviceId;
        task.title = title;
        task.intervalValue = interval;
        task.intervalUnit = ((Spinner) findViewById(R.id.spIntervalUnit)).getSelectedItem().toString();
        task.nextDueDate = dueMillis;
        task.priority = ((Spinner) findViewById(R.id.spPriority)).getSelectedItem().toString();
        task.comment = ((EditText) findViewById(R.id.etTaskComment)).getText().toString().trim();
        String cost = ((EditText) findViewById(R.id.etCost)).getText().toString().trim();
        task.cost = cost.isEmpty() ? null : Double.parseDouble(cost);
        task.consumables = ((EditText) findViewById(R.id.etConsumables)).getText().toString().trim();
        task.isActive = 1;

        if (editing == null) task.id = dao.insert(task); else dao.update(task);
        ReminderScheduler.scheduleTaskReminder(this, task);
        finish();
    }

    private void completeTask() {
        if (editing == null) return;
        repository.completeTask(this, editing, System.currentTimeMillis(), "", editing.consumables, editing.cost);
        Toast.makeText(this, "Отмечено как выполнено", Toast.LENGTH_SHORT).show();
        finish();
    }

    private void setSpinnerSelection(Spinner spinner, String value) {
        ArrayAdapter<String> adapter = (ArrayAdapter<String>) spinner.getAdapter();
        int pos = adapter.getPosition(value);
        if (pos >= 0) spinner.setSelection(pos);
    }
}
