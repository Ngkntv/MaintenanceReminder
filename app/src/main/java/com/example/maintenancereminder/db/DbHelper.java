package com.example.maintenancereminder.db;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

public class DbHelper extends SQLiteOpenHelper {

    public static final String DB_NAME = "maintenance.db";
    public static final int DB_VERSION = 3;

    public static final String TABLE_EQUIPMENT = "equipment";
    public static final String TABLE_TASKS = "maintenance_tasks";
    public static final String TABLE_HISTORY = "service_history";
    public static final String TABLE_SETTINGS = "app_settings";

    public DbHelper(Context context){
        super(context, DB_NAME, null, DB_VERSION);
    }

    @Override
    public void onConfigure(SQLiteDatabase db) {
        super.onConfigure(db);
        db.setForeignKeyConstraintsEnabled(true);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE " + TABLE_EQUIPMENT + " (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "name TEXT NOT NULL," +
                "category TEXT," +
                "notes TEXT," +
                "photo_uri TEXT," +
                "barcode TEXT," +
                "last_service_date INTEGER," +
                "service_interval_days INTEGER," +
                "next_service_date INTEGER" +
                ")");

        db.execSQL("CREATE TABLE " + TABLE_TASKS + " (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "device_id INTEGER NOT NULL," +
                "title TEXT NOT NULL," +
                "interval_value INTEGER NOT NULL," +
                "interval_unit TEXT NOT NULL," +
                "next_due_date INTEGER NOT NULL," +
                "priority TEXT NOT NULL," +
                "comment TEXT," +
                "cost REAL," +
                "consumables TEXT," +
                "is_active INTEGER NOT NULL DEFAULT 1," +
                "FOREIGN KEY(device_id) REFERENCES " + TABLE_EQUIPMENT + "(id) ON DELETE CASCADE" +
                ")");

        db.execSQL("CREATE TABLE " + TABLE_HISTORY + " (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "task_id INTEGER NOT NULL," +
                "device_id INTEGER NOT NULL," +
                "completion_date INTEGER NOT NULL," +
                "completion_comment TEXT," +
                "consumables TEXT," +
                "cost REAL," +
                "previous_due_date INTEGER NOT NULL," +
                "FOREIGN KEY(task_id) REFERENCES " + TABLE_TASKS + "(id) ON DELETE CASCADE," +
                "FOREIGN KEY(device_id) REFERENCES " + TABLE_EQUIPMENT + "(id) ON DELETE CASCADE" +
                ")");

        db.execSQL("CREATE TABLE " + TABLE_SETTINGS + " (" +
                "id INTEGER PRIMARY KEY CHECK (id = 1)," +
                "notification_preset TEXT NOT NULL," +
                "notification_hour INTEGER NOT NULL" +
                ")");

        db.execSQL("CREATE INDEX IF NOT EXISTS idx_equipment_name ON " + TABLE_EQUIPMENT + "(name)");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_tasks_device_due ON " + TABLE_TASKS + "(device_id, next_due_date)");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_history_device_date ON " + TABLE_HISTORY + "(device_id, completion_date DESC)");
        db.execSQL("INSERT INTO " + TABLE_SETTINGS + "(id, notification_preset, notification_hour) VALUES(1, 'MORNING', 9)");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        if (oldVersion < 3) {
            db.execSQL("ALTER TABLE " + TABLE_EQUIPMENT + " ADD COLUMN category TEXT");
            db.execSQL("CREATE TABLE IF NOT EXISTS " + TABLE_TASKS + " (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                    "device_id INTEGER NOT NULL," +
                    "title TEXT NOT NULL," +
                    "interval_value INTEGER NOT NULL," +
                    "interval_unit TEXT NOT NULL," +
                    "next_due_date INTEGER NOT NULL," +
                    "priority TEXT NOT NULL," +
                    "comment TEXT," +
                    "cost REAL," +
                    "consumables TEXT," +
                    "is_active INTEGER NOT NULL DEFAULT 1," +
                    "FOREIGN KEY(device_id) REFERENCES " + TABLE_EQUIPMENT + "(id) ON DELETE CASCADE" +
                    ")");
            db.execSQL("CREATE TABLE IF NOT EXISTS " + TABLE_HISTORY + " (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                    "task_id INTEGER NOT NULL," +
                    "device_id INTEGER NOT NULL," +
                    "completion_date INTEGER NOT NULL," +
                    "completion_comment TEXT," +
                    "consumables TEXT," +
                    "cost REAL," +
                    "previous_due_date INTEGER NOT NULL," +
                    "FOREIGN KEY(task_id) REFERENCES " + TABLE_TASKS + "(id) ON DELETE CASCADE," +
                    "FOREIGN KEY(device_id) REFERENCES " + TABLE_EQUIPMENT + "(id) ON DELETE CASCADE" +
                    ")");
            db.execSQL("CREATE TABLE IF NOT EXISTS " + TABLE_SETTINGS + " (" +
                    "id INTEGER PRIMARY KEY CHECK (id = 1)," +
                    "notification_preset TEXT NOT NULL," +
                    "notification_hour INTEGER NOT NULL" +
                    ")");
            db.execSQL("INSERT OR IGNORE INTO " + TABLE_SETTINGS + "(id, notification_preset, notification_hour) VALUES(1, 'MORNING', 9)");
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_tasks_device_due ON " + TABLE_TASKS + "(device_id, next_due_date)");
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_history_device_date ON " + TABLE_HISTORY + "(device_id, completion_date DESC)");
        }
    }
}
