package com.example.maintenancereminder.db;

import android.content.ContentValues;
import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.util.Log;

import com.example.maintenancereminder.model.MaintenanceTask;
import com.example.maintenancereminder.model.ServiceHistoryEntry;
import com.example.maintenancereminder.notification.ReminderScheduler;
import com.example.maintenancereminder.util.DateUtils;


public class MaintenanceRepository {
    private static final String TAG = "MaintenanceRepository";
    private final DbHelper dbHelper;
    private final MaintenanceTaskDao taskDao;
    private final HistoryDao historyDao;

    public MaintenanceRepository(Context context) {
        this.dbHelper = new DbHelper(context);
        this.taskDao = new MaintenanceTaskDao(context);
        this.historyDao = new HistoryDao(context);
    }

    public void completeTask(Context context, MaintenanceTask task, long completionDate, String comment, String consumables, Double cost) {
        if (task == null || task.id == null || task.deviceId == null || task.intervalValue == null || task.intervalValue <= 0) {
            throw new IllegalArgumentException("Task data is invalid for completion");
        }

        SQLiteDatabase db = dbHelper.getWritableDatabase();
        Long newDueDate = null;
        db.beginTransaction();
        try {
            ServiceHistoryEntry entry = new ServiceHistoryEntry();
            entry.taskId = task.id;
            entry.deviceId = task.deviceId;
            entry.completionDate = completionDate;
            entry.completionComment = comment;
            entry.consumables = consumables;
            entry.cost = cost;
            entry.previousDueDate = task.nextDueDate;

            ContentValues historyValues = new ContentValues();
            historyValues.put("task_id", entry.taskId);
            historyValues.put("device_id", entry.deviceId);
            historyValues.put("completion_date", entry.completionDate);
            historyValues.put("completion_comment", entry.completionComment);
            historyValues.put("consumables", entry.consumables);
            if (entry.cost == null) historyValues.putNull("cost"); else historyValues.put("cost", entry.cost);
            historyValues.put("previous_due_date", entry.previousDueDate);
            long historyId = db.insert(DbHelper.TABLE_HISTORY, null, historyValues);
            if (historyId <= 0) throw new IllegalStateException("Failed to save history for task=" + task.id);

            long baseDueDate = task.nextDueDate == null ? completionDate : task.nextDueDate;
            long calculatedDueDate = DateUtils.calculateNextDueDate(baseDueDate, task.intervalValue, task.intervalUnit);
            ContentValues taskValues = new ContentValues();
            taskValues.put("next_due_date", calculatedDueDate);
            int updatedRows = db.update(DbHelper.TABLE_TASKS, taskValues, "id=?", new String[]{String.valueOf(task.id)});
            if (updatedRows <= 0) throw new IllegalStateException("Failed to update next_due_date for task=" + task.id);

            db.setTransactionSuccessful();
            newDueDate = calculatedDueDate;
        } finally {
            db.endTransaction();
        }

        if (newDueDate != null) {
            task.nextDueDate = newDueDate;
            ReminderScheduler.scheduleTaskReminder(context, task);
        }
    }

    public boolean rollbackLastCompletion(Context context, long taskId) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        MaintenanceTask taskForReschedule = null;
        db.beginTransaction();
        try {
            MaintenanceTask task = taskDao.getById(taskId);
            ServiceHistoryEntry last = historyDao.getLastByTask(taskId);
            if (task == null || last == null) return false;

            db.delete(DbHelper.TABLE_HISTORY, "id=?", new String[]{String.valueOf(last.id)});
            ContentValues cv = new ContentValues();
            cv.put("next_due_date", last.previousDueDate);
            db.update(DbHelper.TABLE_TASKS, cv, "id=?", new String[]{String.valueOf(taskId)});
            db.setTransactionSuccessful();

            task.nextDueDate = last.previousDueDate;
            taskForReschedule = task;
        } finally {
            db.endTransaction();
        }

        if (taskForReschedule != null) {
            ReminderScheduler.scheduleTaskReminder(context, taskForReschedule);
            return true;
        }
        return false;
    }

    public int deleteTask(Context context, long taskId) {
        ReminderScheduler.cancelTaskReminder(context, taskId);
        int deleted = taskDao.delete(taskId);
        Log.d(TAG, "deleteTask taskId=" + taskId + " deletedRows=" + deleted);
        return deleted;
    }

    public int deleteDevice(Context context, long deviceId) {
        for (MaintenanceTask task : taskDao.getByDevice(deviceId)) {
            ReminderScheduler.cancelTaskReminder(context, task.id);
        }
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        return db.delete(DbHelper.TABLE_EQUIPMENT, "id=?", new String[]{String.valueOf(deviceId)});
    }
}
