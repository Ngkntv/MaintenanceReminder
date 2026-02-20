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

    private static final String[] LIST_COLUMNS = {
            "id",
            "name",
            "barcode",
            "last_service_date",
            "service_interval_days",
            "next_service_date"
    };

    public EquipmentDao(Context context) {
        this.dbHelper = new DbHelper(context);
    }

    public long insert(Equipment e) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put("name", e.name);
        cv.put("barcode", e.barcode);
        cv.put("last_service_date", e.lastServiceDate);
        cv.put("service_interval_days", e.serviceIntervalDays);
        cv.put("next_service_date", e.nextServiceDate);
        cv.put("notes", e.notes);
        cv.put("photo_uri", e.photoUri);
        return db.insert(DbHelper.TABLE_EQUIPMENT, null, cv);
    }

    public int update(Equipment e) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put("name", e.name);
        cv.put("barcode", e.barcode);
        cv.put("last_service_date", e.lastServiceDate);
        cv.put("service_interval_days", e.serviceIntervalDays);
        cv.put("next_service_date", e.nextServiceDate);
        cv.put("notes", e.notes);
        cv.put("photo_uri", e.photoUri);
        return db.update(DbHelper.TABLE_EQUIPMENT, cv, "id = ?", new String[]{String.valueOf(e.id)});
    }

    public List<Equipment> getAll() {
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        Cursor c = db.query(
                DbHelper.TABLE_EQUIPMENT,
                LIST_COLUMNS,
                null,
                null,
                null,
                null,
                "next_service_date ASC"
        );

        List<Equipment> list = new ArrayList<>();
        while (c.moveToNext()) {
            list.add(fromListCursor(c));
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

    private Equipment fromListCursor(Cursor c) {
        Equipment e = new Equipment();
        e.id = c.getLong(c.getColumnIndexOrThrow("id"));
        e.name = c.getString(c.getColumnIndexOrThrow("name"));
        e.barcode = c.getString(c.getColumnIndexOrThrow("barcode"));
        e.lastServiceDate = c.getLong(c.getColumnIndexOrThrow("last_service_date"));
        e.serviceIntervalDays = c.getLong(c.getColumnIndexOrThrow("service_interval_days"));
        e.nextServiceDate = c.getLong(c.getColumnIndexOrThrow("next_service_date"));
        return e;
    }

    private Equipment fromCursor(Cursor c) {
        Equipment e = new Equipment();
        e.id = c.getLong(c.getColumnIndexOrThrow("id"));
        e.name = c.getString(c.getColumnIndexOrThrow("name"));
        e.barcode = c.getString(c.getColumnIndexOrThrow("barcode"));
        e.lastServiceDate = c.getLong(c.getColumnIndexOrThrow("last_service_date"));
        e.serviceIntervalDays = c.getLong(c.getColumnIndexOrThrow("service_interval_days"));
        e.nextServiceDate = c.getLong(c.getColumnIndexOrThrow("next_service_date"));
        e.notes = c.getString(c.getColumnIndexOrThrow("notes"));
        int photoIdx = c.getColumnIndex("photo_uri");
        e.photoUri = photoIdx >= 0 ? c.getString(photoIdx) : null;
        return e;
    }
}
