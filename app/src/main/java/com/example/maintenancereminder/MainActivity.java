package com.example.maintenancereminder;

import android.Manifest;
import android.app.DatePickerDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.maintenancereminder.db.EquipmentDao;
import com.example.maintenancereminder.db.MaintenanceRepository;
import com.example.maintenancereminder.db.MaintenanceTaskDao;
import com.example.maintenancereminder.model.Equipment;
import com.example.maintenancereminder.model.MaintenanceTask;
import com.example.maintenancereminder.notification.ReminderScheduler;
import com.example.maintenancereminder.ui.AddEquipmentActivity;
import com.example.maintenancereminder.ui.DeviceDetailActivity;
import com.example.maintenancereminder.ui.HistoryActivity;
import com.example.maintenancereminder.ui.SettingsActivity;
import com.example.maintenancereminder.ui.TaskEditActivity;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.chip.Chip;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.progressindicator.LinearProgressIndicator;
import com.google.android.material.snackbar.Snackbar;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity {

    private static final DateTimeFormatter RU_DATE_FORMATTER = DateTimeFormatter.ofPattern("dd.MM.yyyy", Locale.getDefault());

    private EquipmentDao equipmentDao;
    private MaintenanceTaskDao taskDao;
    private MaintenanceRepository repository;
    private EquipmentAdapter adapter;
    private BottomSheetBehavior<View> bottomSheetBehavior;
    private final ExecutorService ioExecutor = Executors.newSingleThreadExecutor();

    private TextView tvSheetDevice;
    private TextView tvSheetDue;
    private Chip chipStatus;
    private Button btnPrimaryAction;
    private LinearProgressIndicator progressToService;
    private TextView tvProgressHint;
    private Button btnMoveDate;
    private Button btnEditFrequency;
    private Button btnReminder;
    private Button btnOpenDevice;
    private TextView tvDetailLastDate;
    private TextView tvDetailPeriodicity;
    private TextView tvDetailNote;

    private MaintenanceTaskDao.TaskWithDevice nearestTask;

    private final ActivityResultLauncher<String> notificationsPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), isGranted -> {});
    private final ActivityResultLauncher<Intent> addEquipmentLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() == RESULT_OK) {
                    loadData();
                }
            });
    private final ActivityResultLauncher<Intent> editTaskLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> loadData());

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        equipmentDao = new EquipmentDao(this);
        taskDao = new MaintenanceTaskDao(this);
        repository = new MaintenanceRepository(this);

        setupEquipmentList();
        setupBottomSheet();

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

    private void setupEquipmentList() {
        RecyclerView recyclerView = findViewById(R.id.recyclerViewEquipment);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        adapter = new EquipmentAdapter(new java.util.ArrayList<>(), item -> {
            Intent intent = new Intent(MainActivity.this, DeviceDetailActivity.class);
            intent.putExtra("equipment_id", item.id);
            startActivity(intent);
        });
        recyclerView.setAdapter(adapter);
    }

    private void setupBottomSheet() {
        View bottomSheet = findViewById(R.id.nextMaintenanceBottomSheet);
        bottomSheetBehavior = BottomSheetBehavior.from(bottomSheet);
        bottomSheetBehavior.setState(BottomSheetBehavior.STATE_COLLAPSED);

        tvSheetDevice = findViewById(R.id.tvSheetDevice);
        tvSheetDue = findViewById(R.id.tvSheetDue);
        chipStatus = findViewById(R.id.chipStatus);
        btnPrimaryAction = findViewById(R.id.btnPrimaryAction);
        progressToService = findViewById(R.id.progressToService);
        tvProgressHint = findViewById(R.id.tvProgressHint);
        btnMoveDate = findViewById(R.id.btnMoveDate);
        btnEditFrequency = findViewById(R.id.btnEditFrequency);
        btnReminder = findViewById(R.id.btnReminder);
        btnOpenDevice = findViewById(R.id.btnOpenDevice);
        tvDetailLastDate = findViewById(R.id.tvDetailLastDate);
        tvDetailPeriodicity = findViewById(R.id.tvDetailPeriodicity);
        tvDetailNote = findViewById(R.id.tvDetailNote);

        btnPrimaryAction.setOnClickListener(v -> onPrimaryActionClick());
        btnMoveDate.setOnClickListener(v -> onMoveDateClick());
        btnEditFrequency.setOnClickListener(v -> onEditFrequencyClick());
        btnReminder.setOnClickListener(v -> onReminderClick());
        btnOpenDevice.setOnClickListener(v -> onOpenDeviceClick());
    }

    private void loadData() {
        ioExecutor.execute(() -> {
            List<Equipment> list = equipmentDao.getAllWithNearestDue();
            MaintenanceTaskDao.TaskWithDevice candidate = taskDao.getNearestTaskForMainScreen();
            runOnUiThread(() -> {
                adapter.setItems(list);
                View empty = findViewById(R.id.tvEmptyState);
                empty.setVisibility(list.isEmpty() ? View.VISIBLE : View.GONE);
                nearestTask = candidate;
                bindBottomSheet();
            });
        });
    }

    private void bindBottomSheet() {
        if (nearestTask == null || nearestTask.task == null) {
            tvSheetDevice.setText("Нет задач обслуживания");
            tvSheetDue.setText("Добавьте устройство или задачу");
            chipStatus.setVisibility(View.GONE);
            btnPrimaryAction.setText("Добавить устройство");
            tvProgressHint.setText("Прогресс появится после создания задачи");
            progressToService.setIndeterminate(true);
            btnMoveDate.setEnabled(false);
            btnEditFrequency.setEnabled(false);
            btnReminder.setEnabled(false);
            btnOpenDevice.setEnabled(false);
            tvDetailLastDate.setText("Последнее обслуживание: —");
            tvDetailPeriodicity.setText("Периодичность: —");
            tvDetailNote.setText("Примечание: —");
            return;
        }

        MaintenanceTask task = nearestTask.task;
        LocalDate dueDate = toLocalDate(task.nextDueDate);
        long daysRemaining = java.time.temporal.ChronoUnit.DAYS.between(LocalDate.now(), dueDate);

        tvSheetDevice.setText("Устройство: " + safe(nearestTask.deviceName));
        tvSheetDue.setText(formatDueLine(daysRemaining, dueDate));
        chipStatus.setVisibility(View.VISIBLE);
        updateStatusChip(daysRemaining);

        btnPrimaryAction.setText("Отметить выполненным");
        btnMoveDate.setEnabled(true);
        btnEditFrequency.setEnabled(true);
        btnReminder.setEnabled(true);
        btnOpenDevice.setEnabled(true);

        boolean scheduled = ReminderScheduler.isReminderScheduled(this, task.id);
        btnReminder.setText(scheduled ? "Проверить напоминание" : "Добавить напоминание");

        bindProgress(task, dueDate, daysRemaining);
        tvDetailLastDate.setText("Последнее обслуживание: " + (nearestTask.lastMaintenanceDate == null
                ? "—"
                : RU_DATE_FORMATTER.format(toLocalDate(nearestTask.lastMaintenanceDate))));
        tvDetailPeriodicity.setText("Периодичность: " + formatPeriodicity(task));
        tvDetailNote.setText("Примечание: " + (task.comment == null || task.comment.trim().isEmpty() ? "—" : task.comment.trim()));
    }

    private void bindProgress(MaintenanceTask task, LocalDate dueDate, long daysRemaining) {
        if (nearestTask.lastMaintenanceDate != null) {
            LocalDate lastDate = toLocalDate(nearestTask.lastMaintenanceDate);
            long totalDays = java.time.temporal.ChronoUnit.DAYS.between(lastDate, dueDate);
            long elapsedDays = java.time.temporal.ChronoUnit.DAYS.between(lastDate, LocalDate.now());
            if (totalDays > 0) {
                int progress = (int) Math.min(100L, Math.max(0L, (elapsedDays * 100L) / totalDays));
                progressToService.setIndeterminate(false);
                progressToService.setProgressCompat(progress, true);
                tvProgressHint.setText("Пройдено " + progress + "% до плановой даты");
                return;
            }
        }

        progressToService.setIndeterminate(false);
        int proxyProgress = (int) Math.max(5, Math.min(95, 100 - (daysRemaining * 10)));
        progressToService.setProgressCompat(proxyProgress, true);
        tvProgressHint.setText("Оценочный прогресс по оставшимся дням");
    }

    private void onPrimaryActionClick() {
        if (nearestTask == null || nearestTask.task == null) {
            addEquipmentLauncher.launch(new Intent(this, AddEquipmentActivity.class));
            return;
        }

        MaintenanceTask task = nearestTask.task;
        ioExecutor.execute(() -> {
            try {
                repository.completeTask(this, task, System.currentTimeMillis(), "", null, null);
                String nextDate = RU_DATE_FORMATTER.format(toLocalDate(task.nextDueDate));
                runOnUiThread(() -> {
                    Snackbar snackbar = Snackbar.make(findViewById(R.id.mainRoot), "Задача выполнена. Следующая дата: " + nextDate, Snackbar.LENGTH_LONG);
                    snackbar.setAction("Отменить", v -> ioExecutor.execute(() -> {
                        repository.rollbackLastCompletion(this, task.id);
                        runOnUiThread(this::loadData);
                    }));
                    snackbar.show();
                    loadData();
                });
            } catch (Exception e) {
                runOnUiThread(() -> Toast.makeText(this, "Не удалось отметить задачу выполненной", Toast.LENGTH_SHORT).show());
            }
        });
    }

    private void onMoveDateClick() {
        if (nearestTask == null || nearestTask.task == null) return;
        MaintenanceTask task = nearestTask.task;
        LocalDate dueDate = toLocalDate(task.nextDueDate);
        DatePickerDialog dialog = new DatePickerDialog(
                this,
                (view, year, month, dayOfMonth) -> {
                    LocalDate pickedDate = LocalDate.of(year, month + 1, dayOfMonth);
                    long pickedMillis = pickedDate.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli();
                    ioExecutor.execute(() -> {
                        taskDao.updateNextDueDate(task.id, pickedMillis);
                        task.nextDueDate = pickedMillis;
                        ReminderScheduler.scheduleTaskReminder(this, task);
                        runOnUiThread(this::loadData);
                    });
                },
                dueDate.getYear(),
                dueDate.getMonthValue() - 1,
                dueDate.getDayOfMonth()
        );
        dialog.show();
    }

    private void onEditFrequencyClick() {
        if (nearestTask == null || nearestTask.task == null) return;
        Intent intent = new Intent(this, TaskEditActivity.class);
        intent.putExtra("device_id", nearestTask.task.deviceId);
        intent.putExtra("task_id", nearestTask.task.id);
        editTaskLauncher.launch(intent);
    }

    private void onReminderClick() {
        if (nearestTask == null || nearestTask.task == null) return;
        MaintenanceTask task = nearestTask.task;
        boolean scheduled = ReminderScheduler.isReminderScheduled(this, task.id);
        if (scheduled) {
            boolean rescheduled = ReminderScheduler.scheduleTaskReminder(this, task);
            Toast.makeText(this, rescheduled ? "Напоминание активно" : "Не удалось проверить напоминание", Toast.LENGTH_SHORT).show();
            return;
        }
        boolean added = ReminderScheduler.scheduleTaskReminder(this, task);
        Toast.makeText(this, added ? "Напоминание добавлено" : "Не удалось добавить напоминание", Toast.LENGTH_SHORT).show();
        loadData();
    }

    private void onOpenDeviceClick() {
        if (nearestTask == null || nearestTask.task == null) return;
        Intent intent = new Intent(this, DeviceDetailActivity.class);
        intent.putExtra("equipment_id", nearestTask.task.deviceId);
        startActivity(intent);
    }

    private void updateStatusChip(long daysRemaining) {
        if (daysRemaining <= 1) {
            chipStatus.setText("Срочно");
            chipStatus.setChipBackgroundColorResource(android.R.color.holo_red_light);
            chipStatus.setTextColor(Color.WHITE);
        } else if (daysRemaining <= 7) {
            chipStatus.setText("Скоро");
            chipStatus.setChipBackgroundColorResource(android.R.color.holo_orange_light);
            chipStatus.setTextColor(Color.BLACK);
        } else {
            chipStatus.setText("Ок");
            chipStatus.setChipBackgroundColorResource(android.R.color.holo_green_light);
            chipStatus.setTextColor(Color.WHITE);
        }
    }

    private String formatDueLine(long daysRemaining, LocalDate dueDate) {
        String daysPart;
        if (daysRemaining < 0) {
            long overdueDays = Math.abs(daysRemaining);
            daysPart = overdueDays + " " + dayWord(overdueDays) + " просрочено";
        } else if (daysRemaining == 0) {
            daysPart = "0 дней";
        } else {
            daysPart = daysRemaining + " " + dayWord(daysRemaining);
        }
        return "Через " + daysPart + " • " + RU_DATE_FORMATTER.format(dueDate);
    }

    private String dayWord(long days) {
        long rem100 = days % 100;
        long rem10 = days % 10;
        if (rem100 >= 11 && rem100 <= 14) return "дней";
        if (rem10 == 1) return "день";
        if (rem10 >= 2 && rem10 <= 4) return "дня";
        return "дней";
    }

    private String formatPeriodicity(MaintenanceTask task) {
        if (task.intervalValue == null || task.intervalValue <= 0 || task.intervalUnit == null) return "—";
        long value = task.intervalValue;
        switch (task.intervalUnit) {
            case "WEEKS":
                return "каждые " + value + " нед.";
            case "MONTHS":
                return "каждые " + value + " мес.";
            case "YEARS":
                return "каждые " + value + " г.";
            default:
                return "каждые " + value + " " + dayWord(value);
        }
    }

    private LocalDate toLocalDate(long epochMillis) {
        return Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()).toLocalDate();
    }

    private String safe(String value) {
        return value == null ? "—" : value;
    }

    private void requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return;
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                == PackageManager.PERMISSION_GRANTED) return;
        notificationsPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        ioExecutor.shutdown();
    }
}
