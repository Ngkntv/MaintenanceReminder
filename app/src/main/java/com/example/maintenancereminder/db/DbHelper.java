package com.example.maintenancereminder.db;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

public class DbHelper extends SQLiteOpenHelper {

    public static final String DB_NAME = "maintenance.db";
    public static final int DB_VERSION = 2;

    public static final String TABLE_EQUIPMENT = "equipment";
    private static final String INDEX_NEXT_SERVICE_DATE = "idx_equipment_next_service_date";

    public DbHelper(Context context){
        super(context, DB_NAME, null, DB_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        String sql = "CREATE TABLE " + TABLE_EQUIPMENT + " (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "name TEXT NOT NULL," +
                "barcode TEXT NOT NULL," +
                "last_service_date INTEGER NOT NULL," +
                "service_interval_days INTEGER NOT NULL," +
                "next_service_date INTEGER NOT NULL," +
                "notes TEXT," +
                "photo_uri TEXT" +
                ")";
        db.execSQL(sql);
        db.execSQL("CREATE INDEX IF NOT EXISTS " + INDEX_NEXT_SERVICE_DATE
                + " ON " + TABLE_EQUIPMENT + "(next_service_date)");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        if (oldVersion < 2) {
            db.execSQL("CREATE INDEX IF NOT EXISTS " + INDEX_NEXT_SERVICE_DATE
                    + " ON " + TABLE_EQUIPMENT + "(next_service_date)");
        }
    }
}
