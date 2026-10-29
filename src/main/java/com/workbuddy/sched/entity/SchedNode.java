package com.workbuddy.sched.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 集群节点表 SCHED_NODE（选主 / 心跳 / 分片）。
 */
@Data
@TableName("SCHED_NODE")
public class SchedNode {
    @TableId(value = "NODE_ID", type = IdType.INPUT)
    private String nodeId;

    @TableField("HOST")
    private String host;

    @TableField("PORT")
    private Integer port;

    @TableField("IS_LEADER")
    private String isLeader;     // Y/N

    @TableField("SHARD_MOD")
    private Integer shardMod;

    @TableField("SHARD_TOTAL")
    private Integer shardTotal;

    @TableField("LAST_HEARTBEAT")
    private LocalDateTime lastHeartbeat;

    @TableField("STATUS")
    private String status;        // UP/DOWN
}
