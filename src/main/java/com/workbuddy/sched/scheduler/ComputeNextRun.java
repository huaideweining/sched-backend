package com.workbuddy.sched.scheduler;

import com.workbuddy.sched.common.TimeZoneUtil;
import com.workbuddy.sched.entity.SchedTask;

import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 下一次触发时间计算（核心算法）。
 * 移植自前端已与 JS 在 TZ=Asia/Shanghai 下逐例验证一致的算法；
 * 全链路使用统一时区 Asia/Shanghai，绝不读取 JVM 默认时区。
 *
 * 断点建议：在 weekly / monthly 分支下断，对照前端表现验证（含月末收敛、跨时区偏移）。
 */
public class ComputeNextRun {

    private static final Pattern AT = Pattern.compile("(\\d{1,2}):(\\d{2})");
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("M月d日 HH:mm");

    private final ZoneId zone = TimeZoneUtil.ZONE;

    /**
     * 计算下次触发（返回 epoch 毫秒）。
     *
     * @param task     任务定义（使用 mode/intervalMin/atTime/weekday/monthDay）
     * @param baseTsMs 基准时间戳（毫秒）；为 null 时取「当前/上次实际触发」
     */
    public long compute(SchedTask task, Long baseTsMs) {
        return computeAt(task, baseTsMs, ZonedDateTime.now(zone));
    }

    /** 可注入 now 的重载（便于单测固定时刻） */
    public long computeAt(SchedTask task, Long baseTsMs, ZonedDateTime now) {
        long nowMs = now.toInstant().toEpochMilli();
        long base = (baseTsMs != null) ? baseTsMs : nowMs;

        if ("INTERVAL".equalsIgnoreCase(task.getMode())) {
            long mins = (task.getIntervalMin() != null ? task.getIntervalMin() : 30);
            return base + mins * 60_000L;
        }

        int hh = 9, mm = 0;
        if (task.getAtTime() != null) {
            Matcher m = AT.matcher(task.getAtTime().trim());
            if (m.matches()) {
                hh = Integer.parseInt(m.group(1));
                mm = Integer.parseInt(m.group(2));
            }
        }

        ZonedDateTime d = now.withHour(hh).withMinute(mm).withSecond(0).withNano(0);
        long dMs = d.toInstant().toEpochMilli();

        if ("WEEKLY".equalsIgnoreCase(task.getMode())) {
            int wd = (task.getWeekday() != null) ? task.getWeekday() : 1; // 1..7，7=周日
            int jsNowDow = now.getDayOfWeek().getValue() % 7;             // 周一1..周日0
            int add = (wd - jsNowDow + 7) % 7;
            if (add == 0 && dMs <= nowMs) {
                add = 7;
            }
            return d.plusDays(add).toInstant().toEpochMilli();
        }

        if ("MONTHLY".equalsIgnoreCase(task.getMode())) {
            int dom = clamp(task.getMonthDay(), 1, 31);
            int lenThis = now.toLocalDate().lengthOfMonth();
            ZonedDateTime x = now.withDayOfMonth(Math.min(dom, lenThis))
                    .withHour(hh).withMinute(mm).withSecond(0).withNano(0);
            if (x.toInstant().toEpochMilli() <= nowMs) {
                ZonedDateTime nm = x.plusMonths(1);
                int lenNext = nm.toLocalDate().lengthOfMonth();
                x = nm.withDayOfMonth(Math.min(dom, lenNext))
                        .withHour(hh).withMinute(mm).withSecond(0).withNano(0);
            }
            return x.toInstant().toEpochMilli();
        }

        // DAILY（默认）
        if (dMs <= nowMs) {
            d = d.plusDays(1);
        }
        return d.toInstant().toEpochMilli();
    }

    /** epoch 毫秒 -> 统一时区 LocalDateTime（用于持久化 NEXT_FIRE_TIME） */
    public LocalDateTime toLocal(long epochMs) {
        return java.time.Instant.ofEpochMilli(epochMs).atZone(zone).toLocalDateTime();
    }

    /** 待办标题时间戳：M月d日 HH:mm（统一时区） */
    public String stamp(long epochMs) {
        return toLocal(epochMs).format(STAMP);
    }

    private static int clamp(Integer v, int lo, int hi) {
        int x = (v != null) ? v : lo;
        return Math.max(lo, Math.min(hi, x));
    }

    /** 仅用于单测：暴露 jsNowDow 计算一致性校验（周几映射） */
    public static int jsDow(DayOfWeek dow) {
        return dow.getValue() % 7;
    }
}
