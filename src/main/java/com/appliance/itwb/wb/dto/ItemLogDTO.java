package com.appliance.itwb.wb.dto;

import java.io.Serializable;

/**
 * 事项每日完成明细 DTO（wire 层）。logDate/createdAt/updatedAt 为 ISO 字符串或 null；
 * progressPct 为完成百分比（双精度）。
 */
public class ItemLogDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    private String id;
    private String itemId;
    private String logDate;
    private Double progressPct;
    private String content;
    private String blocker;
    private String mood;
    private String createdAt;
    private String updatedAt;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getItemId() {
        return itemId;
    }

    public void setItemId(String itemId) {
        this.itemId = itemId;
    }

    public String getLogDate() {
        return logDate;
    }

    public void setLogDate(String logDate) {
        this.logDate = logDate;
    }

    public Double getProgressPct() {
        return progressPct;
    }

    public void setProgressPct(Double progressPct) {
        this.progressPct = progressPct;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public String getBlocker() {
        return blocker;
    }

    public void setBlocker(String blocker) {
        this.blocker = blocker;
    }

    public String getMood() {
        return mood;
    }

    public void setMood(String mood) {
        this.mood = mood;
    }

    public String getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(String createdAt) {
        this.createdAt = createdAt;
    }

    public String getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(String updatedAt) {
        this.updatedAt = updatedAt;
    }
}
