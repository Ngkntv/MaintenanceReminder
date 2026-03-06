package com.example.maintenancereminder.ui;

import android.content.Context;
import android.os.Bundle;
import android.util.Log;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.example.maintenancereminder.R;
import com.example.maintenancereminder.db.MaintenanceRepository;
import com.example.maintenancereminder.db.MaintenanceTaskDao;
import com.example.maintenancereminder.model.MaintenanceTask;
import com.example.maintenancereminder.notification.ReminderScheduler;
import com.example.maintenancereminder.util.DateUtils;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class TaskEditActivity extends AppCompatActivity {
    private static final String TAG = "TaskEditActivity";

    private long deviceId;
    private long taskId;
    private MaintenanceTaskDao dao;
    private MaintenanceRepository repository;
    private MaintenanceTask editing;
    private final ExecutorService ioExecutor = Executors.newSingleThreadExecutor();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_task_edit);
        dao = new MaintenanceTaskDao(this);
        repository = new MaintenanceRepository(this);

        deviceId = getIntent().getLongExtra("device_id", -1L);
        if (deviceId == -1L) {
            // Backward compatibility with callers that still pass equipment_id.
            deviceId = getIntent().getLongExtra("equipment_id", -1L);
        }
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
                ((EditText) findViewById(R.id.etStartDate)).setText(DateUtils.formatDate(editing.nextDueDate));
                setSpinnerSelection(spUnit, editing.intervalUnit == null ? "DAYS" : editing.intervalUnit);
                setSpinnerSelection(spPriority, editing.priority);
                updateCalculatedNextDueDate();
            }
        }

        Button btnComplete = findViewById(R.id.btnCompleteTask);
        btnComplete.setOnClickListener(v -> completeTask());
        btnComplete.setEnabled(taskId != -1L);

        findViewById(R.id.btnRollback).setOnClickListener(v -> ioExecutor.execute(() -> {
            try {
                if (taskId != -1L && repository.rollbackLastCompletion(this, taskId)) {
                    runOnUiThread(() -> Toast.makeText(this, "Последнее выполнение отменено", Toast.LENGTH_SHORT).show());
                }
            } catch (Exception e) {
                Log.e(TAG, "Rollback failed", e);
                runOnUiThread(() -> Toast.makeText(this, "Не удалось откатить выполнение", Toast.LENGTH_SHORT).show());
            }
        }));

        findViewById(R.id.btnDeleteTask).setOnClickListener(v -> {
            if (taskId == -1L) {
                Toast.makeText(this, "Можно удалить только существующую задачу", Toast.LENGTH_SHORT).show();
                return;
            }
            new AlertDialog.Builder(this).setMessage("Удалить задачу?")
                    .setPositiveButton("Удалить", (d, w) -> deleteTask())
                    .setNegativeButton("Отмена", null)
                    .show();
        });

        findViewById(R.id.btnSaveTask).setOnClickListener(v -> saveTask());
    }

    private void saveTask() {
        if (deviceId <= 0L) {
            Toast.makeText(this, "Не удалось определить устройство для задачи", Toast.LENGTH_SHORT).show();
            Log.e(TAG, "saveTask failed: invalid deviceId=" + deviceId);
            return;
        }

        String title = ((EditText) findViewById(R.id.etTaskTitle)).getText().toString().trim();
        String intervalStr = ((EditText) findViewById(R.id.etIntervalValue)).getText().toString().trim();
        String startDateStr = ((EditText) findViewById(R.id.etStartDate)).getText().toString().trim();
        if (title.isEmpty() || intervalStr.isEmpty()) {
            Toast.makeText(this, "Заполните обязательные поля", Toast.LENGTH_SHORT).show();
            return;
        }

        long interval;
        LocalDate baseDate;
        try {
            interval = Long.parseLong(intervalStr);
            if (interval <= 0) throw new IllegalArgumentException("interval <= 0");
            baseDate = startDateStr.isEmpty() ? LocalDate.now() : LocalDate.parse(startDateStr);
        } catch (Exception e) {
            Toast.makeText(this, "Проверьте период и формат даты yyyy-MM-dd", Toast.LENGTH_SHORT).show();
            return;
        }

        String intervalUnit = ((Spinner) findViewById(R.id.spIntervalUnit)).getSelectedItem().toString();
        LocalDate nextDueDate = DateUtils.calculateNextDueDate(baseDate, interval, intervalUnit);
        updateCalculatedNextDueDate(nextDueDate);

        MaintenanceTask task = editing == null ? new MaintenanceTask() : editing;
        task.deviceId = deviceId;
        task.title = title;
        task.intervalValue = interval;
        task.intervalUnit = intervalUnit;
        task.nextDueDate = nextDueDate.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli();
        task.priority = ((Spinner) findViewById(R.id.spPriority)).getSelectedItem().toString();
        if (task.comment == null) task.comment = "";
        task.cost = null;
        task.consumables = null;
        task.isActive = 1;
        final Context appContext = getApplicationContext();

        ioExecutor.execute(() -> {
            boolean isNewTask = editing == null;
            try {
                if (isNewTask) {
                    long id = dao.insertOrThrow(task);
                    task.id = id;
                } else {
                    int rows = dao.update(task);
                    if (rows <= 0) throw new IllegalStateException("Update task failed for id=" + task.id);
                }
            } catch (Exception e) {
                Log.e(TAG, "Failed to save task", e);
                String errorType = e.getClass().getSimpleName();
                runOnUiThread(() -> Toast.makeText(
                        this,
                        "Не удалось сохранить задачу (" + errorType + ")",
                        Toast.LENGTH_SHORT
                ).show());
                return;
            }

            boolean scheduled = ReminderScheduler.scheduleTaskReminder(appContext, task);
            boolean testScheduled = isNewTask && ReminderScheduler.scheduleTestReminderAfter5Seconds(appContext, task);
            runOnUiThread(() -> {
                if (!scheduled) {
                    Toast.makeText(this, "Задача сохранена, но напоминание не установлено", Toast.LENGTH_LONG).show();
                } else if (isNewTask) {
                    Toast.makeText(this,
                            testScheduled
                                    ? "Тестовое напоминание появится через 5 секунд"
                                    : "Задача сохранена. Не удалось запланировать тестовое напоминание",
                            Toast.LENGTH_LONG).show();
                }
                finish();
            });
        });
    }

    private void completeTask() {
        if (editing == null || editing.id == null || editing.deviceId == null || editing.intervalValue == null || editing.intervalValue <= 0) {
            Toast.makeText(this, "Некорректные данные задачи", Toast.LENGTH_SHORT).show();
            Log.w(TAG, "completeTask skipped due invalid editing state");
            return;
        }

        ioExecutor.execute(() -> {
            try {
                if (editing.intervalUnit == null) {
                    editing.intervalUnit = "DAYS";
                }
                repository.completeTask(this, editing, System.currentTimeMillis(), "", null, null);
                runOnUiThread(() -> {
                    Toast.makeText(this, "Отмечено как выполнено", Toast.LENGTH_SHORT).show();
                    finish();
                });
            } catch (Exception e) {
                Log.e(TAG, "Failed to complete task", e);
                runOnUiThread(() -> Toast.makeText(this, "Ошибка при выполнении задачи", Toast.LENGTH_SHORT).show());
            }
        });
    }

    private void deleteTask() {
        ioExecutor.execute(() -> {
            try {
                int deleted = repository.deleteTask(this, taskId);
                runOnUiThread(() -> {
                    if (deleted > 0) {
                        Toast.makeText(this, "Задача удалена", Toast.LENGTH_SHORT).show();
                        finish();
                    } else {
                        Toast.makeText(this, "Задача не найдена", Toast.LENGTH_SHORT).show();
                    }
                });
            } catch (Exception e) {
                Log.e(TAG, "Delete task failed", e);
                runOnUiThread(() -> Toast.makeText(this, "Не удалось удалить задачу", Toast.LENGTH_SHORT).show());
            }
        });
    }

    private void updateCalculatedNextDueDate() {
        String intervalStr = ((EditText) findViewById(R.id.etIntervalValue)).getText().toString().trim();
        String startDateStr = ((EditText) findViewById(R.id.etStartDate)).getText().toString().trim();
        if (intervalStr.isEmpty()) {
            updateCalculatedNextDueDate(null);
            return;
        }
        try {
            long interval = Long.parseLong(intervalStr);
            LocalDate start = startDateStr.isEmpty() ? LocalDate.now() : LocalDate.parse(startDateStr);
            String unit = ((Spinner) findViewById(R.id.spIntervalUnit)).getSelectedItem().toString();
            updateCalculatedNextDueDate(DateUtils.calculateNextDueDate(start, interval, unit));
        } catch (Exception e) {
            updateCalculatedNextDueDate(null);
        }
    }

    private void updateCalculatedNextDueDate(LocalDate date) {
        TextView tvDate = findViewById(R.id.tvCalculatedNextDueDate);
        tvDate.setText(date == null ? "Следующая дата: —" : "Следующая дата: " + date);
    }

    private void setSpinnerSelection(Spinner spinner, String value) {
        ArrayAdapter<String> adapter = (ArrayAdapter<String>) spinner.getAdapter();
        int pos = adapter.getPosition(value);
        if (pos >= 0) spinner.setSelection(pos);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        ioExecutor.shutdown();
    }
}
