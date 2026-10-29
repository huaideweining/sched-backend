package com.workbuddy.sched.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 待办表 SCHED_TODO（生成的待办，Oracle 为唯一事实源）。
 */
@Data
@TableName("SCHED_TODO")
public class SchedTodo {
    @TableId(value = "TODO_ID", type = IdType.INPUT)
    private Long todoId;

    @TableField("USER_ID")
    private String userId;

    @TableField("TITLE")
    private String title;

    @TableField("PRIORITY")
    private Integer priority;

    @TableField("DUE_DATE")
    private LocalDate dueDate;

    @TableField("DONE")
    private String done;          // Y/N

    @TableField("PROJECT")
    private String project;

    @TableField("SOURCE_TASK_ID")
    private Long sourceTaskId;

    @TableField("SCHEDULED_FIRE_TIME")
    private LocalDateTime scheduledFireTime;

    @TableField("CREATED_DATE")
    private LocalDate createdDate;
}
