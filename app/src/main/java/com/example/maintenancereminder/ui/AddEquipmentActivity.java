package com.example.maintenancereminder.ui;

import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.activity.result.PickVisualMediaRequest;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

import com.example.maintenancereminder.R;
import com.example.maintenancereminder.db.EquipmentDao;
import com.example.maintenancereminder.model.Equipment;
import com.example.maintenancereminder.notification.ReminderScheduler;
import com.example.maintenancereminder.util.DateUtils;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Locale;

import android.net.Uri;
import androidx.activity.result.ActivityResultLauncher;

public class AddEquipmentActivity extends AppCompatActivity {

    private EditText etName, etBarcode, etLastServiceDate, etIntervalDays;
    private EquipmentDao dao;

    private long editingId = -1L;
    private Equipment editingEquipment;

    private final SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.US);

    private ImageView ivPhoto;
    private Button btnPickPhoto;
    private String selectedPhotoUri = null;

    private final ActivityResultLauncher<PickVisualMediaRequest> pickPhotoLauncher =
            registerForActivityResult(new ActivityResultContracts.PickVisualMedia(), uri -> {
                if (uri != null) {
                    selectedPhotoUri = uri.toString();
                    ivPhoto.setImageURI(uri);
                }
            });


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_add_equipment);

        dao = new EquipmentDao(this);

        etName = findViewById(R.id.etName);
        etBarcode = findViewById(R.id.etBarcode);
        etLastServiceDate = findViewById(R.id.etLastServiceDate);
        etIntervalDays = findViewById(R.id.etIntervalDays);
        Button btnSave = findViewById(R.id.btnSave);

        ivPhoto = findViewById(R.id.ivPhoto);
        btnPickPhoto = findViewById(R.id.btnPickPhoto);

        btnPickPhoto.setOnClickListener(v -> {
            PickVisualMediaRequest request = new PickVisualMediaRequest.Builder()
                    .setMediaType(ActivityResultContracts.PickVisualMedia.ImageOnly.INSTANCE)
                    .build();
            pickPhotoLauncher.launch(request);
        });


        editingId = getIntent().getLongExtra("equipment_id", -1L);
        if (editingId != -1L) {
            loadForEdit(editingId);
            btnSave.setText("Обновить");
            setTitle("Редактирование");
        } else {
            setTitle("Добавить оборудование");
        }

        btnSave.setOnClickListener(v -> saveOrUpdate());
    }

    private void loadForEdit(long id) {
        editingEquipment = dao.getById(id);
        if (editingEquipment == null) {
            Toast.makeText(this, "Запись не найдена", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        etName.setText(editingEquipment.name);
        etBarcode.setText(editingEquipment.barcode);
        etLastServiceDate.setText(sdf.format(editingEquipment.lastServiceDate));
        etIntervalDays.setText(String.valueOf(editingEquipment.serviceIntervalDays));
    }

    private void saveOrUpdate() {
        String name = etName.getText().toString().trim();
        String barcode = etBarcode.getText().toString().trim();
        String dateStr = etLastServiceDate.getText().toString().trim();
        String intervalStr = etIntervalDays.getText().toString().trim();

        if (name.isEmpty() || barcode.isEmpty() || dateStr.isEmpty() || intervalStr.isEmpty()) {
            Toast.makeText(this, "Заполни все поля", Toast.LENGTH_SHORT).show();
            return;
        }

        long lastServiceMillis;
        long intervalDays;

        try {
            if (sdf.parse(dateStr) == null) {
                Toast.makeText(this, "Дата в формате yyyy-MM-dd", Toast.LENGTH_SHORT).show();
                return;
            }
            lastServiceMillis = sdf.parse(dateStr).getTime();

            intervalDays = Long.parseLong(intervalStr);
            if (intervalDays <= 0) {
                Toast.makeText(this, "Интервал должен быть > 0", Toast.LENGTH_SHORT).show();
                return;
            }
        } catch (ParseException e) {
            Toast.makeText(this, "Дата в формате yyyy-MM-dd", Toast.LENGTH_SHORT).show();
            return;
        } catch (NumberFormatException e) {
            Toast.makeText(this, "Интервал должен быть числом > 0", Toast.LENGTH_SHORT).show();
            return;
        }

        long nextServiceDate = DateUtils.calcNextServiceDate(lastServiceMillis, intervalDays);

        Equipment target;

        if (editingId == -1L) {
            Equipment e = new Equipment();
            e.name = name;
            e.barcode = barcode;
            e.lastServiceDate = lastServiceMillis;
            e.serviceIntervalDays = intervalDays;
            e.nextServiceDate = nextServiceDate;
            e.notes = "";
            e.photoUri = selectedPhotoUri;

            e.id = dao.insert(e);
            target = e;

            Toast.makeText(this, "Добавлено", Toast.LENGTH_SHORT).show();
        } else {
            editingEquipment.name = name;
            editingEquipment.barcode = barcode;
            editingEquipment.lastServiceDate = lastServiceMillis;
            editingEquipment.serviceIntervalDays = intervalDays;
            editingEquipment.nextServiceDate = nextServiceDate;
            editingEquipment.photoUri = selectedPhotoUri;

            dao.update(editingEquipment);
            target = editingEquipment;

            Toast.makeText(this, "Обновлено", Toast.LENGTH_SHORT).show();
        }

        // Основное напоминание (если используешь)
        try {
            ReminderScheduler.schedule(this, target);
        } catch (Exception ignored) {
        }

        // Тестовое напоминание (сделай в Scheduler именно +10 секунд)
        try {
            ReminderScheduler.scheduleIn10Sec(this, target);
        } catch (Exception ignored) {
        }

        selectedPhotoUri = editingEquipment.photoUri;
        if (selectedPhotoUri != null && !selectedPhotoUri.isEmpty()) {
            ivPhoto.setImageURI(Uri.parse(selectedPhotoUri));
        }

        finish();
    }
}
