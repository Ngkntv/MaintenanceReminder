package com.example.maintenancereminder.model;

public class ServiceHistoryEntry {
    public Long id;
    public Long taskId;
    public Long deviceId;
    public Long completionDate;
    public String completionComment;
    public String consumables;
    public Double cost;
    public Long previousDueDate;

    public String taskTitle;
    public String deviceName;
}
