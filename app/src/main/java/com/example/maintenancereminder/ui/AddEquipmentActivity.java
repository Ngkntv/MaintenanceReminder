package com.example.maintenancereminder.ui;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.PickVisualMediaRequest;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

import com.example.maintenancereminder.R;
import com.example.maintenancereminder.db.EquipmentDao;
import com.example.maintenancereminder.model.Equipment;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class AddEquipmentActivity extends AppCompatActivity {

    private static final String TAG = "AddEquipmentActivity";

    private EditText etName;
    private EditText etCategory;
    private EditText etNote;
    private EquipmentDao dao;
    private long editingId = -1L;
    private Equipment editingEquipment;

    private ImageView ivPhoto;
    private String selectedPhotoUri = null;
    private final ExecutorService ioExecutor = Executors.newSingleThreadExecutor();

    private final ActivityResultLauncher<PickVisualMediaRequest> pickPhotoLauncher =
            registerForActivityResult(new ActivityResultContracts.PickVisualMedia(), uri -> {
                if (uri != null) {
                    selectedPhotoUri = uri.toString();
                    ivPhoto.setImageURI(uri);
                    try {
                        getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    } catch (Exception e) {
                        Log.w(TAG, "Unable to persist URI permission", e);
                    }
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_add_equipment);

        dao = new EquipmentDao(this);

        etName = findViewById(R.id.etName);
        etCategory = findViewById(R.id.etCategory);
        etNote = findViewById(R.id.etNotes);
        Button btnSave = findViewById(R.id.btnSave);
        Button btnPickPhoto = findViewById(R.id.btnPickPhoto);
        ivPhoto = findViewById(R.id.ivPhoto);

        btnPickPhoto.setOnClickListener(v -> pickPhotoLauncher.launch(new PickVisualMediaRequest.Builder()
                .setMediaType(ActivityResultContracts.PickVisualMedia.ImageOnly.INSTANCE)
                .build()));

        editingId = getIntent().getLongExtra("equipment_id", -1L);
        if (editingId != -1L) {
            loadForEdit(editingId);
            btnSave.setText("Обновить");
        }

        btnSave.setOnClickListener(v -> saveOrUpdate());
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        ioExecutor.shutdownNow();
    }

    private void loadForEdit(long id) {
        editingEquipment = dao.getById(id);
        if (editingEquipment == null) {
            Toast.makeText(this, "Устройство не найдено", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        etName.setText(editingEquipment.name);
        etCategory.setText(editingEquipment.category);
        etNote.setText(editingEquipment.notes);
        selectedPhotoUri = editingEquipment.photoUri;
        if (selectedPhotoUri != null) {
            ivPhoto.setImageURI(android.net.Uri.parse(selectedPhotoUri));
        }
    }

    private void saveOrUpdate() {
        String name = etName.getText().toString().trim();
        if (name.isEmpty()) {
            Toast.makeText(this, "Название обязательно", Toast.LENGTH_SHORT).show();
            return;
        }

        String category = etCategory.getText().toString().trim();
        String notes = etNote.getText().toString().trim();

        ioExecutor.execute(() -> {
            try {
                if (editingId == -1L) {
                    Equipment equipment = new Equipment();
                    equipment.name = name;
                    equipment.category = category;
                    equipment.notes = notes;
                    equipment.photoUri = selectedPhotoUri;
                    long newId = dao.insert(equipment);
                    if (newId <= 0) {
                        throw new IllegalStateException("Insert returned invalid id=" + newId);
                    }
                } else {
                    editingEquipment.name = name;
                    editingEquipment.category = category;
                    editingEquipment.notes = notes;
                    editingEquipment.photoUri = selectedPhotoUri;
                    dao.update(editingEquipment);
                }
                runOnUiThread(this::finish);
            } catch (Exception e) {
                Log.e(TAG, "Failed to save equipment", e);
                runOnUiThread(() -> Toast.makeText(this, "Ошибка сохранения устройства", Toast.LENGTH_SHORT).show());
            }
        });
    }
}
