package com.example.maintenancereminder.db;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import com.example.maintenancereminder.model.AppSettings;

public class SettingsDao {
    private final DbHelper dbHelper;

    public SettingsDao(Context context) {
        this.dbHelper = new DbHelper(context);
    }

    public AppSettings getSettings() {
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        Cursor c = db.query(DbHelper.TABLE_SETTINGS, null, "id=1", null, null, null, null);
        AppSettings settings = new AppSettings();
        settings.notificationPreset = "MORNING";
        settings.notificationHour = 9;
        if (c.moveToFirst()) {
            settings.notificationPreset = c.getString(c.getColumnIndexOrThrow("notification_preset"));
            settings.notificationHour = c.getInt(c.getColumnIndexOrThrow("notification_hour"));
        }
        c.close();
        return settings;
    }

    public void update(String preset, int hour) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put("notification_preset", preset);
        cv.put("notification_hour", hour);
        db.update(DbHelper.TABLE_SETTINGS, cv, "id=1", null);
    }
}
