package com.example.maintenancereminder.ui;

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

    private EditText etTaskTitle;
    private EditText etIntervalValue;
    private EditText etStartDate;
    private Spinner spIntervalUnit;
    private Spinner spPriority;
    private TextView tvCalculatedDueDate;
    private final ExecutorService ioExecutor = Executors.newSingleThreadExecutor();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_task_edit);
        dao = new MaintenanceTaskDao(this);
        repository = new MaintenanceRepository(this);

        deviceId = getIntent().getLongExtra("device_id", -1L);
        taskId = getIntent().getLongExtra("task_id", -1L);

        if (deviceId <= 0) {
            Toast.makeText(this, "Некорректный идентификатор устройства", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        etTaskTitle = findViewById(R.id.etTaskTitle);
        etIntervalValue = findViewById(R.id.etIntervalValue);
        etStartDate = findViewById(R.id.etStartDate);
        spIntervalUnit = findViewById(R.id.spIntervalUnit);
        spPriority = findViewById(R.id.spPriority);
        tvCalculatedDueDate = findViewById(R.id.tvCalculatedDueDate);

        spIntervalUnit.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item,
                new String[]{"DAYS", "WEEKS", "MONTHS", "YEARS"}));
        spPriority.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item,
                new String[]{"LOW", "MEDIUM", "HIGH"}));

        etIntervalValue.setOnFocusChangeListener((v, hasFocus) -> {
            if (!hasFocus) refreshCalculatedDueDatePreview();
        });
        etStartDate.setOnFocusChangeListener((v, hasFocus) -> {
            if (!hasFocus) refreshCalculatedDueDatePreview();
        });

        if (taskId != -1L) {
            editing = dao.getById(taskId);
            if (editing != null) {
                etTaskTitle.setText(editing.title);
                etIntervalValue.setText(String.valueOf(editing.intervalValue));
                // startDate editable; по умолчанию показываем сегодня
                etStartDate.setText(DateUtils.formatDate(System.currentTimeMillis()));
                setSpinnerSelection(spIntervalUnit, editing.intervalUnit);
                setSpinnerSelection(spPriority, editing.priority);
                tvCalculatedDueDate.setText("Текущая следующая дата: " + DateUtils.formatDate(editing.nextDueDate));
            }
        } else {
            etStartDate.setText(DateUtils.formatDate(System.currentTimeMillis()));
        }

        Button btnComplete = findViewById(R.id.btnCompleteTask);
        btnComplete.setOnClickListener(v -> completeTask());
        btnComplete.setEnabled(taskId != -1L);

        findViewById(R.id.btnRollback).setOnClickListener(v -> rollbackLastCompletion());

        findViewById(R.id.btnDeleteTask).setOnClickListener(v -> {
            if (taskId == -1L) {
                Toast.makeText(this, "Сначала сохраните задачу", Toast.LENGTH_SHORT).show();
                return;
            }
            new AlertDialog.Builder(this)
                    .setMessage("Удалить задачу?")
                    .setPositiveButton("Удалить", (d, w) -> deleteTask())
                    .setNegativeButton("Отмена", null)
                    .show();
        });

        findViewById(R.id.btnSaveTask).setOnClickListener(v -> saveTask());
        refreshCalculatedDueDatePreview();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        ioExecutor.shutdownNow();
    }

    private void saveTask() {
        String title = etTaskTitle.getText().toString().trim();
        String intervalStr = etIntervalValue.getText().toString().trim();
        String startDateStr = etStartDate.getText().toString().trim();

        if (title.isEmpty() || intervalStr.isEmpty()) {
            Toast.makeText(this, "Заполните обязательные поля", Toast.LENGTH_SHORT).show();
            return;
        }

        final long interval;
        final long baseMillis;
        final long nextDueMillis;
        final String intervalUnit;

        try {
            interval = Long.parseLong(intervalStr);
            if (interval <= 0) {
                Toast.makeText(this, "Периодичность должна быть > 0", Toast.LENGTH_SHORT).show();
                return;
            }

            LocalDate baseDate = startDateStr.isEmpty()
                    ? LocalDate.now()
                    : LocalDate.parse(startDateStr);
            baseMillis = baseDate.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli();
            intervalUnit = spIntervalUnit.getSelectedItem().toString();
            nextDueMillis = DateUtils.calculateNextDueDate(baseMillis, interval, intervalUnit);
        } catch (Exception e) {
            Log.e(TAG, "Invalid task form", e);
            Toast.makeText(this, "Проверьте формат даты yyyy-MM-dd и период", Toast.LENGTH_SHORT).show();
            return;
        }

        MaintenanceTask task = editing == null ? new MaintenanceTask() : editing;
        task.deviceId = deviceId;
        task.title = title;
        task.intervalValue = interval;
        task.intervalUnit = intervalUnit;
        task.nextDueDate = nextDueMillis;
        task.priority = spPriority.getSelectedItem().toString();
        task.isActive = 1;

        ioExecutor.execute(() -> {
            try {
                if (editing == null) {
                    task.id = dao.insert(task);
                    if (task.id == null || task.id <= 0) {
                        throw new IllegalStateException("Insert task failed, id=" + task.id);
                    }
                } else {
                    int updated = dao.update(task);
                    if (updated <= 0) {
                        throw new IllegalStateException("Update task failed, rows=" + updated);
                    }
                }

                ReminderScheduler.scheduleTaskReminder(this, task);
                runOnUiThread(this::finish);
            } catch (Exception e) {
                Log.e(TAG, "Failed to save task", e);
                runOnUiThread(() -> Toast.makeText(this, "Ошибка сохранения задачи", Toast.LENGTH_SHORT).show());
            }
        });
    }

    private void completeTask() {
        if (taskId <= 0 || editing == null || editing.id == null || editing.deviceId == null) {
            Toast.makeText(this, "Невозможно отметить выполнение: данные задачи повреждены", Toast.LENGTH_SHORT).show();
            Log.w(TAG, "completeTask skipped: taskId=" + taskId + ", editing=" + editing);
            return;
        }

        ioExecutor.execute(() -> {
            try {
                repository.completeTask(this, editing, System.currentTimeMillis(), null, null, null);
                runOnUiThread(() -> {
                    Toast.makeText(this, "Отмечено как выполнено", Toast.LENGTH_SHORT).show();
                    finish();
                });
            } catch (Exception e) {
                Log.e(TAG, "Crash while completing task", e);
                runOnUiThread(() -> Toast.makeText(this, "Ошибка при завершении задачи", Toast.LENGTH_SHORT).show());
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
                runOnUiThread(() -> Toast.makeText(this, "Ошибка удаления задачи", Toast.LENGTH_SHORT).show());
            }
        });
    }

    private void rollbackLastCompletion() {
        if (taskId <= 0) return;
        ioExecutor.execute(() -> {
            try {
                boolean ok = repository.rollbackLastCompletion(this, taskId);
                runOnUiThread(() -> Toast.makeText(this,
                        ok ? "Последнее выполнение отменено" : "Откат недоступен",
                        Toast.LENGTH_SHORT).show());
            } catch (Exception e) {
                Log.e(TAG, "Rollback failed", e);
                runOnUiThread(() -> Toast.makeText(this, "Ошибка отката", Toast.LENGTH_SHORT).show());
            }
        });
    }

    private void refreshCalculatedDueDatePreview() {
        try {
            String intervalStr = etIntervalValue.getText().toString().trim();
            if (intervalStr.isEmpty()) {
                tvCalculatedDueDate.setText("Следующая дата будет рассчитана автоматически");
                return;
            }
            long interval = Long.parseLong(intervalStr);
            if (interval <= 0) {
                tvCalculatedDueDate.setText("Периодичность должна быть > 0");
                return;
            }
            String startDateStr = etStartDate.getText().toString().trim();
            LocalDate base = startDateStr.isEmpty() ? LocalDate.now() : LocalDate.parse(startDateStr);
            long baseMillis = base.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli();
            long due = DateUtils.calculateNextDueDate(baseMillis, interval, spIntervalUnit.getSelectedItem().toString());
            tvCalculatedDueDate.setText("Следующая дата: " + DateUtils.formatDate(due));
        } catch (Exception ignored) {
            tvCalculatedDueDate.setText("Следующая дата будет рассчитана автоматически");
        }
    }

    private void setSpinnerSelection(Spinner spinner, String value) {
        ArrayAdapter<String> adapter = (ArrayAdapter<String>) spinner.getAdapter();
        int pos = adapter.getPosition(value);
        if (pos >= 0) spinner.setSelection(pos);
    }
}
