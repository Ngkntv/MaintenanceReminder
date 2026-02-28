package com.example.maintenancereminder;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.maintenancereminder.db.EquipmentDao;
import com.example.maintenancereminder.db.HistoryDao;
import com.example.maintenancereminder.db.MaintenanceRepository;
import com.example.maintenancereminder.db.MaintenanceTaskDao;
import com.example.maintenancereminder.model.Equipment;
import com.example.maintenancereminder.model.MaintenanceTask;
import com.example.maintenancereminder.model.ServiceHistoryEntry;
import com.example.maintenancereminder.notification.ReminderScheduler;
import com.example.maintenancereminder.ui.AddEquipmentActivity;
import com.example.maintenancereminder.ui.DeviceDetailActivity;
import com.example.maintenancereminder.ui.HistoryActivity;
import com.example.maintenancereminder.ui.SettingsActivity;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.chip.Chip;
import com.google.android.material.datepicker.MaterialDatePicker;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.snackbar.Snackbar;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity {

    private static final DateTimeFormatter DISPLAY_DATE_FORMAT = DateTimeFormatter.ofPattern("dd.MM.yyyy", new Locale("ru"));

    private EquipmentDao equipmentDao;
    private MaintenanceTaskDao taskDao;
    private HistoryDao historyDao;
    private MaintenanceRepository repository;
    private EquipmentAdapter adapter;

    private TextView tvSheetDevice;
    private TextView tvSheetDue;
    private TextView tvLastService;
    private TextView tvPeriod;
    private TextView tvNote;
    private Chip chipStatus;
    private com.google.android.material.button.MaterialButton btnPrimaryAction;
    private com.google.android.material.button.MaterialButton btnMoveDate;
    private com.google.android.material.button.MaterialButton btnChangePeriod;
    private com.google.android.material.button.MaterialButton btnReminderAction;
    private com.google.android.material.button.MaterialButton btnOpenDevice;

    private NearestMaintenance currentNearest;
    private Equipment selectedEquipment;

    private final ExecutorService ioExecutor = Executors.newSingleThreadExecutor();
    private final ActivityResultLauncher<String> notificationsPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), isGranted -> {
            });
    private final ActivityResultLauncher<Intent> addEquipmentLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() == RESULT_OK) {
                    refreshSelectedEquipmentSheet();
                }
            });

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        equipmentDao = new EquipmentDao(this);
        taskDao = new MaintenanceTaskDao(this);
        historyDao = new HistoryDao(this);
        repository = new MaintenanceRepository(this);

        RecyclerView recyclerView = findViewById(R.id.recyclerViewEquipment);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        adapter = new EquipmentAdapter(new java.util.ArrayList<>(), item -> {
            Intent intent = new Intent(MainActivity.this, DeviceDetailActivity.class);
            intent.putExtra("equipment_id", item.id);
            startActivity(intent);
        }, this::showBottomSheetForEquipment);
        recyclerView.setAdapter(adapter);

        initBottomSheet();
        setupActions();

        FloatingActionButton fab = findViewById(R.id.fabAdd);
        fab.setOnClickListener(view ->
                addEquipmentLauncher.launch(new Intent(MainActivity.this, AddEquipmentActivity.class)));
        findViewById(R.id.btnSettings).setOnClickListener(v -> startActivity(new Intent(this, SettingsActivity.class)));
        findViewById(R.id.btnJournal).setOnClickListener(v -> startActivity(new Intent(this, HistoryActivity.class)));


        requestNotificationPermissionIfNeeded();
        rescheduleRemindersInBackground();
    }


    private void rescheduleRemindersInBackground() {
        ioExecutor.execute(() -> ReminderScheduler.rescheduleAll(getApplicationContext()));
    }

    private void initBottomSheet() {
        View sheet = findViewById(R.id.bottomSheet);
        BottomSheetBehavior<View> bottomSheetBehavior = BottomSheetBehavior.from(sheet);
        bottomSheetBehavior.setHideable(true);
        bottomSheetBehavior.setDraggable(true);
        bottomSheetBehavior.setPeekHeight(getResources().getDimensionPixelSize(R.dimen.main_bottom_sheet_peek_height), true);
        sheet.post(() -> bottomSheetBehavior.setState(BottomSheetBehavior.STATE_HIDDEN));

        tvSheetDevice = findViewById(R.id.tvSheetDevice);
        tvSheetDue = findViewById(R.id.tvSheetDue);
        tvLastService = findViewById(R.id.tvSheetLastService);
        tvPeriod = findViewById(R.id.tvSheetPeriod);
        tvNote = findViewById(R.id.tvSheetNote);
        chipStatus = findViewById(R.id.chipStatus);

        btnPrimaryAction = findViewById(R.id.btnPrimaryAction);
        btnMoveDate = findViewById(R.id.btnMoveDate);
        btnChangePeriod = findViewById(R.id.btnChangePeriod);
        btnReminderAction = findViewById(R.id.btnReminderAction);
        btnOpenDevice = findViewById(R.id.btnOpenDevice);
    }

    private void setupActions() {
        btnPrimaryAction.setOnClickListener(v -> {
            if (currentNearest == null) {
                if (selectedEquipment != null && selectedEquipment.id != null) {
                    Intent intent = new Intent(MainActivity.this, DeviceDetailActivity.class);
                    intent.putExtra("equipment_id", selectedEquipment.id);
                    startActivity(intent);
                } else {
                    addEquipmentLauncher.launch(new Intent(MainActivity.this, AddEquipmentActivity.class));
                }
                return;
            }
            completeCurrentTask();
        });

        chipStatus.setOnClickListener(v -> {
            BottomSheetBehavior<View> behavior = BottomSheetBehavior.from(findViewById(R.id.bottomSheet));
            int state = behavior.getState();
            behavior.setState(state == BottomSheetBehavior.STATE_EXPANDED
                    ? BottomSheetBehavior.STATE_COLLAPSED
                    : BottomSheetBehavior.STATE_EXPANDED);
        });

        btnMoveDate.setOnClickListener(v -> {
            if (currentNearest == null) return;
            MaterialDatePicker<Long> picker = MaterialDatePicker.Builder.datePicker()
                    .setSelection(currentNearest.task.nextDueDate)
                    .setTitleText("Перенести дату")
                    .build();
            picker.addOnPositiveButtonClickListener(selection -> updateDueDate(selection));
            picker.show(getSupportFragmentManager(), "move_date");
        });

        btnChangePeriod.setOnClickListener(v -> {
            if (currentNearest == null) return;
            final long[] periods = new long[]{7, 14, 30, 90};
            String[] labels = new String[]{"7 дней", "14 дней", "30 дней", "90 дней"};
            new AlertDialog.Builder(this)
                    .setTitle("Изменить периодичность")
                    .setItems(labels, (dialog, which) -> updatePeriodicity(periods[which]))
                    .show();
        });

        btnReminderAction.setOnClickListener(v -> {
            if (currentNearest == null) return;
            boolean wasScheduled = ReminderScheduler.isReminderScheduled(this, currentNearest.task.id);
            boolean scheduled = ReminderScheduler.scheduleTaskReminder(this, currentNearest.task);
            long triggerAt = ReminderScheduler.getReminderTriggerTime(this, currentNearest.task);
            String triggerText = triggerAt > 0
                    ? Instant.ofEpochMilli(triggerAt).atZone(ZoneId.systemDefault()).toLocalDateTime()
                    .format(DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm", new Locale("ru")))
                    : "—";
            Snackbar.make(findViewById(R.id.bottomSheet),
                            (wasScheduled ? "Напоминание проверено: " : "Напоминание добавлено: ") + triggerText,
                            Snackbar.LENGTH_LONG)
                    .show();
            if (scheduled) refreshSelectedEquipmentSheet();
        });

        btnOpenDevice.setOnClickListener(v -> {
            Long equipmentId = currentNearest != null && currentNearest.equipment != null
                    ? currentNearest.equipment.id
                    : (selectedEquipment == null ? null : selectedEquipment.id);
            if (equipmentId == null) return;
            Intent intent = new Intent(MainActivity.this, DeviceDetailActivity.class);
            intent.putExtra("equipment_id", equipmentId);
            startActivity(intent);
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadData();
    }

    private void loadData() {
        ioExecutor.execute(() -> {
            java.util.List<Equipment> list = equipmentDao.getAllWithNearestDue();
            runOnUiThread(() -> {
                adapter.setItems(list);
                View empty = findViewById(R.id.tvEmptyState);
                empty.setVisibility(list.isEmpty() ? View.VISIBLE : View.GONE);
                if (selectedEquipment != null && selectedEquipment.id != null) {
                    Equipment matched = null;
                    for (Equipment item : list) {
                        if (item.id != null && item.id.equals(selectedEquipment.id)) {
                            matched = item;
                            break;
                        }
                    }
                    if (matched != null) {
                        selectedEquipment = matched;
                    } else {
                        currentNearest = null;
                        selectedEquipment = null;
                        BottomSheetBehavior<View> behavior = BottomSheetBehavior.from(findViewById(R.id.bottomSheet));
                        behavior.setState(BottomSheetBehavior.STATE_HIDDEN);
                    }
                }
            });
        });
    }

    private void refreshSelectedEquipmentSheet() {
        if (selectedEquipment == null || selectedEquipment.id == null) {
            loadData();
            return;
        }
        loadData();
        showBottomSheetForEquipment(selectedEquipment);
    }

    private void showBottomSheetForEquipment(Equipment equipment) {
        if (equipment == null || equipment.id == null) return;
        selectedEquipment = equipment;
        ioExecutor.execute(() -> {
            MaintenanceTask selectedTask = taskDao.getNearestForDevice(equipment.id);
            NearestMaintenance nearest = null;
            if (selectedTask != null && selectedTask.id != null) {
                ServiceHistoryEntry lastHistory = historyDao.getLastByTask(selectedTask.id);
                nearest = new NearestMaintenance(equipment, selectedTask, lastHistory);
            }
            NearestMaintenance finalNearest = nearest;
            runOnUiThread(() -> {
                currentNearest = finalNearest;
                bindBottomSheet(finalNearest);
                BottomSheetBehavior<View> behavior = BottomSheetBehavior.from(findViewById(R.id.bottomSheet));
                behavior.setState(BottomSheetBehavior.STATE_COLLAPSED);
            });
        });
    }

    private void bindBottomSheet(NearestMaintenance nearest) {
        if (nearest == null) {
            String deviceName = selectedEquipment == null ? "—" : selectedEquipment.name;
            tvSheetDevice.setText("Устройство: " + deviceName);
            tvSheetDue.setText("Нет задач обслуживания");
            chipStatus.setText("Ок");
            tvLastService.setText("Последнее обслуживание: —");
            tvPeriod.setText("Периодичность: —");
            tvNote.setText("Заметка: —");
            btnPrimaryAction.setText(selectedEquipment == null ? "Добавить устройство" : "Открыть карточку устройства");
            btnMoveDate.setEnabled(false);
            btnChangePeriod.setEnabled(false);
            btnReminderAction.setEnabled(false);
            btnOpenDevice.setEnabled(selectedEquipment != null && selectedEquipment.id != null);
            return;
        }

        LocalDate dueDate = Instant.ofEpochMilli(nearest.task.nextDueDate).atZone(ZoneId.systemDefault()).toLocalDate();
        long daysRemaining = ChronoUnit.DAYS.between(LocalDate.now(ZoneId.systemDefault()), dueDate);

        selectedEquipment = nearest.equipment;
        tvSheetDevice.setText("Устройство: " + nearest.equipment.name);
        tvSheetDue.setText(buildDueText(daysRemaining, dueDate));
        chipStatus.setText(resolveStatus(daysRemaining));
        tvLastService.setText("Последнее обслуживание: " + (nearest.lastHistory == null ? "—" : formatDate(nearest.lastHistory.completionDate)));
        tvPeriod.setText("Периодичность: " + nearest.task.intervalValue + " " + mapIntervalUnit(nearest.task.intervalUnit));
        tvNote.setText("Заметка: " + (nearest.task.comment == null || nearest.task.comment.isBlank() ? "—" : nearest.task.comment));

        btnPrimaryAction.setText("Отметить выполненным");
        btnMoveDate.setEnabled(true);
        btnChangePeriod.setEnabled(true);
        btnReminderAction.setEnabled(true);
        btnOpenDevice.setEnabled(true);
        btnReminderAction.setText(ReminderScheduler.isReminderScheduled(this, nearest.task.id)
                ? "Проверить напоминание"
                : "Добавить напоминание");
    }

    private void completeCurrentTask() {
        if (currentNearest == null) return;
        ioExecutor.execute(() -> {
            try {
                MaintenanceTask task = currentNearest.task;
                repository.completeTask(this, task, System.currentTimeMillis(), "", null, null);
                String nextDate = formatDate(task.nextDueDate);
                runOnUiThread(() -> Snackbar.make(findViewById(R.id.bottomSheet),
                                "Задача выполнена. Следующая дата: " + nextDate,
                                Snackbar.LENGTH_LONG)
                        .setAction("Отменить", v -> rollbackCompletion(task.id))
                        .show());
                refreshSelectedEquipmentSheet();
            } catch (Exception e) {
                runOnUiThread(() -> Snackbar.make(findViewById(R.id.bottomSheet), "Не удалось завершить задачу", Snackbar.LENGTH_SHORT).show());
            }
        });
    }

    private void rollbackCompletion(Long taskId) {
        if (taskId == null) return;
        ioExecutor.execute(() -> {
            boolean rolledBack = repository.rollbackLastCompletion(this, taskId);
            runOnUiThread(() -> {
                Snackbar.make(findViewById(R.id.bottomSheet),
                        rolledBack ? "Последнее выполнение отменено" : "Откат недоступен",
                        Snackbar.LENGTH_SHORT).show();
                if (rolledBack) refreshSelectedEquipmentSheet();
            });
        });
    }

    private void updateDueDate(long selectedMillisUtc) {
        if (currentNearest == null) return;
        LocalDate selectedDate = Instant.ofEpochMilli(selectedMillisUtc).atZone(ZoneId.systemDefault()).toLocalDate();
        long localMidnight = selectedDate.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli();
        ioExecutor.execute(() -> {
            try {
                int rows = taskDao.updateNextDueDate(currentNearest.task.id, localMidnight);
                runOnUiThread(() -> {
                    Snackbar.make(findViewById(R.id.bottomSheet), rows > 0 ? "Дата обслуживания обновлена" : "Не удалось обновить дату", Snackbar.LENGTH_SHORT).show();
                    if (rows > 0) refreshSelectedEquipmentSheet();
                });
            } catch (Exception e) {
                runOnUiThread(() -> Snackbar.make(findViewById(R.id.bottomSheet), "Ошибка при обновлении даты", Snackbar.LENGTH_SHORT).show());
            }
        });
    }

    private void updatePeriodicity(long newDays) {
        if (currentNearest == null) return;
        ioExecutor.execute(() -> {
            try {
                MaintenanceTask task = currentNearest.task;
                task.intervalValue = newDays;
                task.intervalUnit = "DAYS";
                int rows = taskDao.update(task);
                runOnUiThread(() -> {
                    Snackbar.make(findViewById(R.id.bottomSheet), rows > 0 ? "Периодичность обновлена" : "Не удалось обновить периодичность", Snackbar.LENGTH_SHORT).show();
                    if (rows > 0) refreshSelectedEquipmentSheet();
                });
            } catch (Exception e) {
                runOnUiThread(() -> Snackbar.make(findViewById(R.id.bottomSheet), "Ошибка при обновлении периодичности", Snackbar.LENGTH_SHORT).show());
            }
        });
    }

    private String buildDueText(long daysRemaining, LocalDate dueDate) {
        if (daysRemaining >= 0) {
            return "Через " + daysRemaining + " " + dayDeclension(daysRemaining) + " • " + dueDate.format(DISPLAY_DATE_FORMAT);
        }
        long overdueDays = Math.abs(daysRemaining);
        return "Просрочено на " + overdueDays + " " + dayDeclension(overdueDays) + " • " + dueDate.format(DISPLAY_DATE_FORMAT);
    }

    private String resolveStatus(long daysRemaining) {
        if (daysRemaining <= 1) return "Срочно";
        if (daysRemaining <= 7) return "Скоро";
        return "Ок";
    }

    private String dayDeclension(long days) {
        long mod10 = days % 10;
        long mod100 = days % 100;
        if (mod10 == 1 && mod100 != 11) return "день";
        if (mod10 >= 2 && mod10 <= 4 && (mod100 < 12 || mod100 > 14)) return "дня";
        return "дней";
    }

    private String formatDate(Long millis) {
        if (millis == null) return "—";
        return Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalDate().format(DISPLAY_DATE_FORMAT);
    }

    private String mapIntervalUnit(String unit) {
        if (unit == null) return "дней";
        switch (unit) {
            case "WEEKS":
                return "недель";
            case "MONTHS":
                return "месяцев";
            case "YEARS":
                return "лет";
            case "DAYS":
            default:
                return "дней";
        }
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

    private static class NearestMaintenance {
        final Equipment equipment;
        final MaintenanceTask task;
        final ServiceHistoryEntry lastHistory;

        NearestMaintenance(Equipment equipment, MaintenanceTask task, ServiceHistoryEntry lastHistory) {
            this.equipment = equipment;
            this.task = task;
            this.lastHistory = lastHistory;
        }
    }
}
