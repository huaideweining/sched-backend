package com.workbuddy.sched.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 任务响应（接口契约）。
 */
@Data
public class TaskResp {
    private Long taskId;
    private String name;
    private String content;
    private String mode;
    private Integer intervalMin;
    private String atTime;
    private Integer weekday;
    private Integer monthDay;
    private Integer priority;
    private String project;
    private String enabled;
    private LocalDateTime nextFireTime;
    private LocalDateTime createdAt;
}
