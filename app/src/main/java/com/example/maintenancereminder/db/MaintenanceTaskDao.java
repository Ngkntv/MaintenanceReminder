package com.example.maintenancereminder.db;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import com.example.maintenancereminder.model.MaintenanceTask;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

public class MaintenanceTaskDao {
    private final DbHelper dbHelper;

    public MaintenanceTaskDao(Context context) {
        this.dbHelper = new DbHelper(context);
    }

    public long insert(MaintenanceTask task) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        ContentValues cv = toValues(task);
        return db.insert(DbHelper.TABLE_TASKS, null, cv);
    }

    public long insertOrThrow(MaintenanceTask task) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        ContentValues cv = toValues(task);
        return db.insertOrThrow(DbHelper.TABLE_TASKS, null, cv);
    }

    public int update(MaintenanceTask task) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        ContentValues cv = toValues(task);
        return db.update(DbHelper.TABLE_TASKS, cv, "id=?", new String[]{String.valueOf(task.id)});
    }

    public int updateNextDueDate(long taskId, long nextDueDate) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put("next_due_date", nextDueDate);
        return db.update(DbHelper.TABLE_TASKS, cv, "id=?", new String[]{String.valueOf(taskId)});
    }

    public List<MaintenanceTask> getByDevice(long deviceId) {
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        Cursor c = db.query(DbHelper.TABLE_TASKS, null, "device_id=?", new String[]{String.valueOf(deviceId)}, null, null, "next_due_date ASC");
        List<MaintenanceTask> list = new ArrayList<>();
        while (c.moveToNext()) {
            list.add(fromCursor(c));
        }
        c.close();
        return list;
    }


    public List<MaintenanceTask> getAllActive() {
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        Cursor c = db.query(DbHelper.TABLE_TASKS, null, "is_active=1", null, null, null, null);
        List<MaintenanceTask> list = new ArrayList<>();
        while (c.moveToNext()) list.add(fromCursor(c));
        c.close();
        return list;
    }

    public TaskWithDevice getNearestTaskForMainScreen() {
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        String sql = "SELECT t.*, e.name AS device_name, " +
                "(SELECT MAX(h.completion_date) FROM " + DbHelper.TABLE_HISTORY + " h WHERE h.task_id = t.id) AS last_completion " +
                "FROM " + DbHelper.TABLE_TASKS + " t " +
                "JOIN " + DbHelper.TABLE_EQUIPMENT + " e ON e.id = t.device_id " +
                "WHERE t.is_active = 1";
        Cursor c = db.rawQuery(sql, null);
        List<TaskWithDevice> all = new ArrayList<>();
        while (c.moveToNext()) {
            MaintenanceTask task = fromCursor(c);
            TaskWithDevice row = new TaskWithDevice();
            row.task = task;
            int deviceNameIdx = c.getColumnIndex("device_name");
            row.deviceName = deviceNameIdx >= 0 ? c.getString(deviceNameIdx) : "";
            int lastCompletionIdx = c.getColumnIndex("last_completion");
            if (lastCompletionIdx >= 0 && !c.isNull(lastCompletionIdx)) {
                row.lastMaintenanceDate = c.getLong(lastCompletionIdx);
            }
            all.add(row);
        }
        c.close();

        if (all.isEmpty()) return null;

        LocalDate today = LocalDate.now();
        Comparator<TaskWithDevice> byDueDate = Comparator.comparingLong(it -> it.task.nextDueDate);
        TaskWithDevice nearestUpcoming = all.stream()
                .filter(it -> !toLocalDate(it.task.nextDueDate).isBefore(today))
                .min(byDueDate)
                .orElse(null);
        if (nearestUpcoming != null) return nearestUpcoming;

        return all.stream()
                .filter(it -> toLocalDate(it.task.nextDueDate).isBefore(today))
                .max(byDueDate)
                .orElse(null);
    }

    private LocalDate toLocalDate(long epochMillis) {
        return Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()).toLocalDate();
    }

    public static class TaskWithDevice {
        public MaintenanceTask task;
        public String deviceName;
        public Long lastMaintenanceDate;
    }
    public MaintenanceTask getById(long id) {
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        Cursor c = db.query(DbHelper.TABLE_TASKS, null, "id=?", new String[]{String.valueOf(id)}, null, null, null);
        MaintenanceTask task = null;
        if (c.moveToFirst()) task = fromCursor(c);
        c.close();
        return task;
    }

    public int delete(long id) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        return db.delete(DbHelper.TABLE_TASKS, "id=?", new String[]{String.valueOf(id)});
    }

    private ContentValues toValues(MaintenanceTask task) {
        ContentValues cv = new ContentValues();
        cv.put("device_id", task.deviceId);
        cv.put("title", task.title);
        cv.put("interval_value", task.intervalValue);
        cv.put("interval_unit", task.intervalUnit);
        cv.put("next_due_date", task.nextDueDate);
        cv.put("priority", task.priority);
        cv.put("comment", task.comment);
        if (task.cost == null) cv.putNull("cost"); else cv.put("cost", task.cost);
        cv.put("consumables", task.consumables);
        cv.put("is_active", task.isActive);
        return cv;
    }

    private MaintenanceTask fromCursor(Cursor c) {
        MaintenanceTask task = new MaintenanceTask();
        task.id = c.getLong(c.getColumnIndexOrThrow("id"));
        task.deviceId = c.getLong(c.getColumnIndexOrThrow("device_id"));
        task.title = c.getString(c.getColumnIndexOrThrow("title"));
        task.intervalValue = c.getLong(c.getColumnIndexOrThrow("interval_value"));
        task.intervalUnit = c.getString(c.getColumnIndexOrThrow("interval_unit"));
        task.nextDueDate = c.getLong(c.getColumnIndexOrThrow("next_due_date"));
        task.priority = c.getString(c.getColumnIndexOrThrow("priority"));
        task.comment = c.getString(c.getColumnIndexOrThrow("comment"));
        int costIdx = c.getColumnIndexOrThrow("cost");
        task.cost = c.isNull(costIdx) ? null : c.getDouble(costIdx);
        task.consumables = c.getString(c.getColumnIndexOrThrow("consumables"));
        task.isActive = c.getInt(c.getColumnIndexOrThrow("is_active"));
        return task;
    }
}
