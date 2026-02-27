package com.example.maintenancereminder.model;

public class MaintenanceTask {
    public Long id;
    public Long deviceId;
    public String title;
    public Long intervalValue;
    public String intervalUnit; // DAYS/WEEKS/MONTHS/YEARS
    public Long nextDueDate;
    public String priority; // LOW/MEDIUM/HIGH
    public String comment;
    public Double cost;
    public String consumables;
    public int isActive;
}
