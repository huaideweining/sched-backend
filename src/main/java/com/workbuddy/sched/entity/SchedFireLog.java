package com.workbuddy.sched.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 触发执行日志表 SCHED_FIRE_LOG（幂等锚点：UK(TASK_ID, SCHEDULED_FIRE_TIME)）。
 */
@Data
@TableName("SCHED_FIRE_LOG")
public class SchedFireLog {
    @TableId(value = "FIRE_ID", type = IdType.INPUT)
    private Long fireId;

    @TableField("TASK_ID")
    private Long taskId;

    @TableField("TRIGGER_ID")
    private Long triggerId;

    @TableField("USER_ID")
    private String userId;

    @TableField("SCHEDULED_FIRE_TIME")
    private LocalDateTime scheduledFireTime;

    @TableField("ACTUAL_FIRE_TIME")
    private LocalDateTime actualFireTime;

    @TableField("STATUS")
    private String status;        // SUCCESS/FAILED

    @TableField("TODO_ID")
    private Long todoId;

    @TableField("RETRY_COUNT")
    private Integer retryCount;

    @TableField("ERROR_MSG")
    private String errorMsg;
}
