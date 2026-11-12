package com.campusquest.entity;

import java.sql.Timestamp;

public class Application {
    private long id;
    private long taskId;
    private long applicantId;
    private String status;
    private Timestamp createdAt;
    private Timestamp abandonedAt;
    private int penaltyApplied;

    public long getId() { return id; }
    public void setId(long id) { this.id = id; }
    public long getTaskId() { return taskId; }
    public void setTaskId(long taskId) { this.taskId = taskId; }
    public long getApplicantId() { return applicantId; }
    public void setApplicantId(long applicantId) { this.applicantId = applicantId; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Timestamp getCreatedAt() { return createdAt; }
    public void setCreatedAt(Timestamp createdAt) { this.createdAt = createdAt; }
    public Timestamp getAbandonedAt() { return abandonedAt; }
    public void setAbandonedAt(Timestamp abandonedAt) { this.abandonedAt = abandonedAt; }
    public int getPenaltyApplied() { return penaltyApplied; }
    public void setPenaltyApplied(int penaltyApplied) { this.penaltyApplied = penaltyApplied; }
}
