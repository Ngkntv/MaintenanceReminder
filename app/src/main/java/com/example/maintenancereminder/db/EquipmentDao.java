package com.example.maintenancereminder.db;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import com.example.maintenancereminder.model.Equipment;

import java.util.ArrayList;
import java.util.List;

public class EquipmentDao {
    private final DbHelper dbHelper;

    public EquipmentDao(Context context) {
        this.dbHelper = new DbHelper(context);
    }

    public long insert(Equipment e) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put("name", e.name);
        cv.put("category", e.category);
        cv.put("notes", e.notes);
        cv.put("photo_uri", e.photoUri);
        return db.insert(DbHelper.TABLE_EQUIPMENT, null, cv);
    }

    public int update(Equipment e) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put("name", e.name);
        cv.put("category", e.category);
        cv.put("notes", e.notes);
        cv.put("photo_uri", e.photoUri);
        return db.update(DbHelper.TABLE_EQUIPMENT, cv, "id = ?", new String[]{String.valueOf(e.id)});
    }

    public List<Equipment> getAllWithNearestDue() {
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        String sql = "SELECT e.*, MIN(t.next_due_date) AS nearest_due " +
                "FROM " + DbHelper.TABLE_EQUIPMENT + " e " +
                "LEFT JOIN " + DbHelper.TABLE_TASKS + " t ON t.device_id = e.id AND t.is_active = 1 " +
                "GROUP BY e.id " +
                "ORDER BY CASE WHEN nearest_due IS NULL THEN 1 ELSE 0 END, nearest_due ASC, e.name ASC";

        Cursor c = db.rawQuery(sql, null);
        List<Equipment> list = new ArrayList<>();
        while (c.moveToNext()) {
            Equipment e = fromCursor(c);
            int nearestIdx = c.getColumnIndex("nearest_due");
            if (nearestIdx >= 0 && !c.isNull(nearestIdx)) {
                e.nearestTaskDueDate = c.getLong(nearestIdx);
            }
            list.add(e);
        }
        c.close();
        return list;
    }

    public Equipment getById(long id) {
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        Cursor c = db.query(DbHelper.TABLE_EQUIPMENT, null, "id = ?", new String[]{String.valueOf(id)}, null, null, null);
        Equipment e = null;
        if (c.moveToFirst()) e = fromCursor(c);
        c.close();
        return e;
    }

    public int delete(Long id) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        return db.delete(DbHelper.TABLE_EQUIPMENT, "id = ?", new String[]{String.valueOf(id)});
    }

    private Equipment fromCursor(Cursor c) {
        Equipment e = new Equipment();
        e.id = c.getLong(c.getColumnIndexOrThrow("id"));
        e.name = c.getString(c.getColumnIndexOrThrow("name"));
        int categoryIdx = c.getColumnIndex("category");
        e.category = categoryIdx >= 0 ? c.getString(categoryIdx) : null;
        int notesIdx = c.getColumnIndex("notes");
        e.notes = notesIdx >= 0 ? c.getString(notesIdx) : null;
        int photoIdx = c.getColumnIndex("photo_uri");
        e.photoUri = photoIdx >= 0 ? c.getString(photoIdx) : null;
        return e;
    }
}
