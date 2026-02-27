package com.example.maintenancereminder.model;

public class Equipment {
    public Long id;
    public String name;
    public String category;
    public String notes;
    public String photoUri;

    // legacy fields kept for backward compatibility with old DB rows/code paths
    public String barcode;
    public Long lastServiceDate;
    public Long serviceIntervalDays;
    public Long nextServiceDate;

    // calculated for list UI
    public Long nearestTaskDueDate;
}
