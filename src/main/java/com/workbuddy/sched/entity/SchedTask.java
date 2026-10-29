package com.workbuddy.sched.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 任务定义表 SCHED_TASK。
 */
@Data
@TableName("SCHED_TASK")
public class SchedTask {
    @TableId(value = "TASK_ID", type = IdType.INPUT) // 由序列/雪花注入
    private Long taskId;

    @TableField("USER_ID")
    private String userId;

    @TableField("NAME")
    private String name;

    @TableField("CONTENT")
    private String content;

    @TableField("\"MODE\"") // Oracle 保留字，必须加引号；建议后续改名为 SCHED_MODE
    private String mode;          // INTERVAL/DAILY/WEEKLY/MONTHLY

    @TableField("INTERVAL_MIN")
    private Integer intervalMin;

    @TableField("AT_TIME")
    private String atTime;        // HH24:MI

    @TableField("WEEKDAY")
    private Integer weekday;      // 1..7

    @TableField("MONTH_DAY")
    private Integer monthDay;     // 1..31

    @TableField("PRIORITY")
    private Integer priority;

    @TableField("PROJECT")
    private String project;

    @TableField("TIMEZONE")
    private String timezone;

    @TableField("ENABLED")
    private String enabled;       // Y/N

    @TableField("CREATED_AT")
    private LocalDateTime createdAt;

    @TableField("UPDATED_AT")
    private LocalDateTime updatedAt;
}
