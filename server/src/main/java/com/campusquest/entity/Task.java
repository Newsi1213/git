package com.campusquest.entity;

import java.sql.Timestamp;

public class Task {
    private long id;
    private String taskNo;
    private String category;
    private String title;
    private String description;
    private int points;
    private String locationDesc;
    private String timeDesc;
    private long publisherId;
    private Long acceptorId;
    private String status;
    private Timestamp createdAt;
    private Timestamp acceptedAt;
    private Timestamp deliveredAt;
    private Timestamp confirmDeadline;
    private Timestamp finishedAt;
    private int offline;
    private String offlineReason;
    private Timestamp offlineAt;

    public long getId() { return id; }
    public void setId(long id) { this.id = id; }
    public String getTaskNo() { return taskNo; }
    public void setTaskNo(String taskNo) { this.taskNo = taskNo; }
    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public int getPoints() { return points; }
    public void setPoints(int points) { this.points = points; }
    public String getLocationDesc() { return locationDesc; }
    public void setLocationDesc(String locationDesc) { this.locationDesc = locationDesc; }
    public String getTimeDesc() { return timeDesc; }
    public void setTimeDesc(String timeDesc) { this.timeDesc = timeDesc; }
    public long getPublisherId() { return publisherId; }
    public void setPublisherId(long publisherId) { this.publisherId = publisherId; }
    public Long getAcceptorId() { return acceptorId; }
    public void setAcceptorId(Long acceptorId) { this.acceptorId = acceptorId; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Timestamp getCreatedAt() { return createdAt; }
    public void setCreatedAt(Timestamp createdAt) { this.createdAt = createdAt; }
    public Timestamp getAcceptedAt() { return acceptedAt; }
    public void setAcceptedAt(Timestamp acceptedAt) { this.acceptedAt = acceptedAt; }
    public Timestamp getDeliveredAt() { return deliveredAt; }
    public void setDeliveredAt(Timestamp deliveredAt) { this.deliveredAt = deliveredAt; }
    public Timestamp getConfirmDeadline() { return confirmDeadline; }
    public void setConfirmDeadline(Timestamp confirmDeadline) { this.confirmDeadline = confirmDeadline; }
    public Timestamp getFinishedAt() { return finishedAt; }
    public void setFinishedAt(Timestamp finishedAt) { this.finishedAt = finishedAt; }
    public int getOffline() { return offline; }
    public void setOffline(int offline) { this.offline = offline; }
    public String getOfflineReason() { return offlineReason; }
    public void setOfflineReason(String offlineReason) { this.offlineReason = offlineReason; }
    public Timestamp getOfflineAt() { return offlineAt; }
    public void setOfflineAt(Timestamp offlineAt) { this.offlineAt = offlineAt; }
}
