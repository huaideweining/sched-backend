package com.workbuddy.sched.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 调度状态表 SCHED_TRIGGER（系统记录核心，Dispatcher 抢占对象）。
 */
@Data
@TableName("SCHED_TRIGGER")
public class SchedTrigger {
    @TableId(value = "TRIGGER_ID", type = IdType.INPUT)
    private Long triggerId;

    @TableField("TASK_ID")
    private Long taskId;

    @TableField("USER_ID")
    private String userId;

    @TableField("PREV_FIRE_TIME")
    private LocalDateTime prevFireTime;

    @TableField("NEXT_FIRE_TIME")
    private LocalDateTime nextFireTime;

    @TableField("STATUS")
    private String status;        // WAITING/ACQUIRED/... 见 TriggerStatus

    @TableField("UPDATED_AT")
    private LocalDateTime updatedAt;
}
