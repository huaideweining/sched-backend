package com.workbuddy.sched.enums;

/**
 * 调度模式（与前端 / 前端算法保持一致）。
 */
public enum TaskMode {
    INTERVAL,   // 按分钟间隔
    DAILY,      // 每天 atTime
    WEEKLY,     // 每周 weekday atTime
    MONTHLY     // 每月 monthDay atTime
}
