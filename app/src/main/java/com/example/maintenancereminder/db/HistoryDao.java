package com.example.maintenancereminder.db;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import com.example.maintenancereminder.model.ServiceHistoryEntry;

import java.util.ArrayList;
import java.util.List;

public class HistoryDao {
    private final DbHelper dbHelper;

    public HistoryDao(Context context) {
        this.dbHelper = new DbHelper(context);
    }

    public long insert(ServiceHistoryEntry entry) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put("task_id", entry.taskId);
        cv.put("device_id", entry.deviceId);
        cv.put("completion_date", entry.completionDate);
        cv.put("completion_comment", entry.completionComment);
        cv.put("consumables", entry.consumables);
        if (entry.cost == null) cv.putNull("cost"); else cv.put("cost", entry.cost);
        cv.put("previous_due_date", entry.previousDueDate);
        return db.insert(DbHelper.TABLE_HISTORY, null, cv);
    }

    public List<ServiceHistoryEntry> getByDevice(long deviceId) {
        return queryWithJoin("h.device_id=?", new String[]{String.valueOf(deviceId)});
    }

    public List<ServiceHistoryEntry> getAll() {
        return queryWithJoin(null, null);
    }

    public ServiceHistoryEntry getLastByTask(long taskId) {
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        Cursor c = db.query(DbHelper.TABLE_HISTORY, null, "task_id=?", new String[]{String.valueOf(taskId)}, null, null, "completion_date DESC", "1");
        ServiceHistoryEntry entry = null;
        if (c.moveToFirst()) entry = fromCursor(c);
        c.close();
        return entry;
    }

    public int deleteById(long id) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        return db.delete(DbHelper.TABLE_HISTORY, "id=?", new String[]{String.valueOf(id)});
    }

    private List<ServiceHistoryEntry> queryWithJoin(String selection, String[] args) {
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        String sql = "SELECT h.*, t.title AS task_title, e.name AS device_name " +
                "FROM " + DbHelper.TABLE_HISTORY + " h " +
                "JOIN " + DbHelper.TABLE_TASKS + " t ON t.id = h.task_id " +
                "JOIN " + DbHelper.TABLE_EQUIPMENT + " e ON e.id = h.device_id ";
        if (selection != null) sql += " WHERE " + selection;
        sql += " ORDER BY h.completion_date DESC";

        Cursor c = db.rawQuery(sql, args);
        List<ServiceHistoryEntry> result = new ArrayList<>();
        while (c.moveToNext()) result.add(fromCursor(c));
        c.close();
        return result;
    }

    private ServiceHistoryEntry fromCursor(Cursor c) {
        ServiceHistoryEntry entry = new ServiceHistoryEntry();
        entry.id = c.getLong(c.getColumnIndexOrThrow("id"));
        entry.taskId = c.getLong(c.getColumnIndexOrThrow("task_id"));
        entry.deviceId = c.getLong(c.getColumnIndexOrThrow("device_id"));
        entry.completionDate = c.getLong(c.getColumnIndexOrThrow("completion_date"));
        entry.completionComment = c.getString(c.getColumnIndexOrThrow("completion_comment"));
        entry.consumables = c.getString(c.getColumnIndexOrThrow("consumables"));
        int costIdx = c.getColumnIndexOrThrow("cost");
        entry.cost = c.isNull(costIdx) ? null : c.getDouble(costIdx);
        entry.previousDueDate = c.getLong(c.getColumnIndexOrThrow("previous_due_date"));
        int titleIdx = c.getColumnIndex("task_title");
        int devIdx = c.getColumnIndex("device_name");
        entry.taskTitle = titleIdx >= 0 ? c.getString(titleIdx) : null;
        entry.deviceName = devIdx >= 0 ? c.getString(devIdx) : null;
        return entry;
    }
}
