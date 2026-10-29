package com.appliance.itwb.wb.dto;

import java.io.Serializable;

/**
 * 项目关键节点 DTO（wire 层）。due 为 ISO 日期/日期时间字符串或 null；done 为布尔。
 */
public class NodeDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    private String id;
    private String projectId;
    private String title;
    private String due;
    private Boolean done;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getProjectId() {
        return projectId;
    }

    public void setProjectId(String projectId) {
        this.projectId = projectId;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDue() {
        return due;
    }

    public void setDue(String due) {
        this.due = due;
    }

    public Boolean getDone() {
        return done;
    }

    public void setDone(Boolean done) {
        this.done = done;
    }
}
