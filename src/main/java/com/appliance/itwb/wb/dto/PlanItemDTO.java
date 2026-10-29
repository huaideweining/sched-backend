package com.appliance.itwb.wb.dto;

import java.io.Serializable;

/**
 * 计划项 DTO（wire 层）。done 为布尔；seq 为排序序号。
 */
public class PlanItemDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    private String id;
    private String planId;
    private String text;
    private Boolean done;
    private Long seq;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getPlanId() {
        return planId;
    }

    public void setPlanId(String planId) {
        this.planId = planId;
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }

    public Boolean getDone() {
        return done;
    }

    public void setDone(Boolean done) {
        this.done = done;
    }

    public Long getSeq() {
        return seq;
    }

    public void setSeq(Long seq) {
        this.seq = seq;
    }
}
