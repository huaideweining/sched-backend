package com.appliance.itwb.wb.dto;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * 项目 DTO（wire 层）。status 为大写枚举；start/end 为 ISO 日期字符串或 null；含 nodes。
 */
public class ProjectDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    private String id;
    private String name;
    private String status;
    private String owner;
    private String start;
    private String end;
    private String note;
    private List<NodeDTO> nodes = new ArrayList<>();

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getOwner() {
        return owner;
    }

    public void setOwner(String owner) {
        this.owner = owner;
    }

    public String getStart() {
        return start;
    }

    public void setStart(String start) {
        this.start = start;
    }

    public String getEnd() {
        return end;
    }

    public void setEnd(String end) {
        this.end = end;
    }

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
    }

    public List<NodeDTO> getNodes() {
        return nodes;
    }

    public void setNodes(List<NodeDTO> nodes) {
        this.nodes = nodes;
    }
}
