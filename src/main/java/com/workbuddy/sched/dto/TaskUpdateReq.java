package com.workbuddy.sched.dto;

import lombok.Data;

/**
 * 更新任务请求（接口契约）。所有字段可选；传 null 表示不改。
 */
@Data
public class TaskUpdateReq {
    private String name;
    private String content;
    private String mode;
    private Integer intervalMin;
    private String atTime;
    private Integer weekday;
    private Integer monthDay;
    private Integer priority;
    private String project;
    private String enabled;       // Y/N
}
