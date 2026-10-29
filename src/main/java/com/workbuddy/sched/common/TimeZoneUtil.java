package com.workbuddy.sched.common;

import java.time.ZoneId;

/**
 * 时区工具：全链路统一 Asia/Shanghai，禁止读取 JVM 默认时区。
 * 断点建议：算法移植与触发时间计算均经此获取 ZoneId，可在此核验。
 */
public final class TimeZoneUtil {
    public static final ZoneId ZONE = ZoneId.of(Constants.TIME_ZONE);

    public static ZoneId zone() {
        return ZONE;
    }

    private TimeZoneUtil() {}
}
