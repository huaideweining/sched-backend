package com.workbuddy.sched.dto;

import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 待办响应（接口契约）。
 */
@Data
public class TodoResp {
    private Long todoId;
    private String title;
    private Integer priority;
    private LocalDate dueDate;
    private String done;
    private String project;
    private Long sourceTaskId;
    private LocalDateTime scheduledFireTime;
    private LocalDate createdDate;
}
