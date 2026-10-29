package com.workbuddy.sched.enums;

/**
 * 触发器状态机。
 * WAITING ->(Dispatcher SKIP LOCKED 抢占)-> ACQUIRED ->(成功)-> WAITING(重算下次)
 *                                            ->(失败超次)-> ERROR
 * WAITING ->(停用/删除)-> PAUSED / 删除
 */
public enum TriggerStatus {
    WAITING,    // 等待触发
    ACQUIRED,   // 已被某节点抢占（执行中）
    COMPLETED,  // 已完成（一次性用，周期任务复用 WAITING）
    PAUSED,     // 暂停
    ERROR       // 执行超次失败（死信）
}
