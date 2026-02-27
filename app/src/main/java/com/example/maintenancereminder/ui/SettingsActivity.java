package com.example.maintenancereminder.ui;

import android.os.Bundle;
import android.widget.RadioGroup;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.maintenancereminder.R;
import com.example.maintenancereminder.db.SettingsDao;
import com.example.maintenancereminder.notification.ReminderScheduler;

public class SettingsActivity extends AppCompatActivity {
    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);
        SettingsDao dao = new SettingsDao(this);
        RadioGroup rg = findViewById(R.id.rgTimePreset);

        String preset = dao.getSettings().notificationPreset;
        if ("DAY".equals(preset)) rg.check(R.id.rbDay);
        else if ("EVENING".equals(preset)) rg.check(R.id.rbEvening);
        else rg.check(R.id.rbMorning);

        findViewById(R.id.btnSaveSettings).setOnClickListener(v -> {
            int checked = rg.getCheckedRadioButtonId();
            String p = "MORNING";
            int hour = 9;
            if (checked == R.id.rbDay) { p = "DAY"; hour = 14; }
            if (checked == R.id.rbEvening) { p = "EVENING"; hour = 19; }
            dao.update(p, hour);
            ReminderScheduler.rescheduleAll(this);
            Toast.makeText(this, "Настройки сохранены", Toast.LENGTH_SHORT).show();
            finish();
        });
    }
}
