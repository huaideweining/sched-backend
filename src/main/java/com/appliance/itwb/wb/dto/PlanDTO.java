package com.appliance.itwb.wb.dto;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * 计划 DTO（wire 层）。period 为大写枚举；含 items。
 */
public class PlanDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    private String id;
    private String title;
    private String period;
    private String createdAt;
    private List<PlanItemDTO> items = new ArrayList<>();

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getPeriod() {
        return period;
    }

    public void setPeriod(String period) {
        this.period = period;
    }

    public String getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(String createdAt) {
        this.createdAt = createdAt;
    }

    public List<PlanItemDTO> getItems() {
        return items;
    }

    public void setItems(List<PlanItemDTO> items) {
        this.items = items;
    }
}
