package com.appliance.itwb.wb.dto;

import java.io.Serializable;

/**
 * 待办/事项 DTO（wire 层）。枚举大写；due/createdAt/completedAt 为 ISO 字符串或 null；
 * lastRemind 为 epoch 毫秒（Long）。
 */
public class ItemDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    private String id;
    private String kind;
    private String title;
    private String type;
    private String projectId;
    private String assignee;
    private String priority;
    private String due;
    private String status;
    private String note;
    private String createdAt;
    private String completedAt;
    private String startTime;
    private String endTime;
    private String spanType;
    private Long lastRemind;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getKind() {
        return kind;
    }

    public void setKind(String kind) {
        this.kind = kind;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getProjectId() {
        return projectId;
    }

    public void setProjectId(String projectId) {
        this.projectId = projectId;
    }

    public String getAssignee() {
        return assignee;
    }

    public void setAssignee(String assignee) {
        this.assignee = assignee;
    }

    public String getPriority() {
        return priority;
    }

    public void setPriority(String priority) {
        this.priority = priority;
    }

    public String getDue() {
        return due;
    }

    public void setDue(String due) {
        this.due = due;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
    }

    public String getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(String createdAt) {
        this.createdAt = createdAt;
    }

    public String getCompletedAt() {
        return completedAt;
    }

    public void setCompletedAt(String completedAt) {
        this.completedAt = completedAt;
    }

    public String getStartTime() {
        return startTime;
    }

    public void setStartTime(String startTime) {
        this.startTime = startTime;
    }

    public String getEndTime() {
        return endTime;
    }

    public void setEndTime(String endTime) {
        this.endTime = endTime;
    }

    public String getSpanType() {
        return spanType;
    }

    public void setSpanType(String spanType) {
        this.spanType = spanType;
    }

    public Long getLastRemind() {
        return lastRemind;
    }

    public void setLastRemind(Long lastRemind) {
        this.lastRemind = lastRemind;
    }
}
