package com.workbuddy.sched.common;

/**
 * 全局常量。
 */
public final class Constants {
    /** 统一时区（已确认不做每用户自定义） */
    public static final String TIME_ZONE = "Asia/Shanghai";

    /** Redis 频道：任务变更广播（Trigger Loader 实时重载） */
    public static final String REDIS_TOPIC_TASK_CHANGE = "sched:task:change";

    /** 待办标题时间戳格式 */
    public static final String STAMP_PATTERN = "M月d日 HH:mm";

    private Constants() {}
}
