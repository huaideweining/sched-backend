package com.appliance.itwb.wb.dto;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * 工作台整库快照（wire 层容器）。GET 返回当前用户全量业务数据；PUT 接收全量并整表替换。
 *
 * <p>所有集合默认初始化为空列表，避免 Jackson（non_null）序列化出 null 导致前端遍历异常。</p>
 */
public class SnapshotDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    private List<MemberDTO> members = new ArrayList<>();
    private List<ProjectDTO> projects = new ArrayList<>();
    private List<ItemDTO> items = new ArrayList<>();
    private List<PlanDTO> plans = new ArrayList<>();
    private SettingsDTO settings;
    private List<NoteDTO> notes = new ArrayList<>();

    public List<MemberDTO> getMembers() {
        return members;
    }

    public void setMembers(List<MemberDTO> members) {
        this.members = members;
    }

    public List<ProjectDTO> getProjects() {
        return projects;
    }

    public void setProjects(List<ProjectDTO> projects) {
        this.projects = projects;
    }

    public List<ItemDTO> getItems() {
        return items;
    }

    public void setItems(List<ItemDTO> items) {
        this.items = items;
    }

    public List<PlanDTO> getPlans() {
        return plans;
    }

    public void setPlans(List<PlanDTO> plans) {
        this.plans = plans;
    }

    public SettingsDTO getSettings() {
        return settings;
    }

    public void setSettings(SettingsDTO settings) {
        this.settings = settings;
    }

    public List<NoteDTO> getNotes() {
        return notes;
    }

    public void setNotes(List<NoteDTO> notes) {
        this.notes = notes;
    }
}
