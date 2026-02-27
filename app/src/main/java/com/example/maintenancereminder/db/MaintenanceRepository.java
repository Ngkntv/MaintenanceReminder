package com.example.maintenancereminder.db;

import android.content.ContentValues;
import android.content.Context;
import android.database.sqlite.SQLiteDatabase;

import com.example.maintenancereminder.model.MaintenanceTask;
import com.example.maintenancereminder.model.ServiceHistoryEntry;
import com.example.maintenancereminder.notification.ReminderScheduler;
import com.example.maintenancereminder.util.DateUtils;


public class MaintenanceRepository {
    private final DbHelper dbHelper;
    private final MaintenanceTaskDao taskDao;
    private final HistoryDao historyDao;

    public MaintenanceRepository(Context context) {
        this.dbHelper = new DbHelper(context);
        this.taskDao = new MaintenanceTaskDao(context);
        this.historyDao = new HistoryDao(context);
    }

    public void completeTask(Context context, MaintenanceTask task, long completionDate, String comment, String consumables, Double cost) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
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
            db.insert(DbHelper.TABLE_HISTORY, null, historyValues);

            long newDueDate = DateUtils.calculateNextDueDate(completionDate, task.intervalValue, task.intervalUnit);
            ContentValues taskValues = new ContentValues();
            taskValues.put("next_due_date", newDueDate);
            db.update(DbHelper.TABLE_TASKS, taskValues, "id=?", new String[]{String.valueOf(task.id)});

            db.setTransactionSuccessful();
            task.nextDueDate = newDueDate;
            ReminderScheduler.scheduleTaskReminder(context, task);
        } finally {
            db.endTransaction();
        }
    }

    public boolean rollbackLastCompletion(Context context, long taskId) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
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
            ReminderScheduler.scheduleTaskReminder(context, task);
            return true;
        } finally {
            db.endTransaction();
        }
    }

    public int deleteTask(Context context, long taskId) {
        ReminderScheduler.cancelTaskReminder(context, taskId);
        return taskDao.delete(taskId);
    }

    public int deleteDevice(Context context, long deviceId) {
        for (MaintenanceTask task : taskDao.getByDevice(deviceId)) {
            ReminderScheduler.cancelTaskReminder(context, task.id);
        }
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        return db.delete(DbHelper.TABLE_EQUIPMENT, "id=?", new String[]{String.valueOf(deviceId)});
    }
}
