package com.workbuddy.sched.scheduler;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.DelayQueue;
import java.util.concurrent.Delayed;
import java.util.concurrent.TimeUnit;

/**
 * 内存时间轮（秒级精确触发）。DelayQueue 按到期时间排序，到点即 take。
 *
 * ⚠️ 关键不变式：每个 triggerId 在轮中至多存在 1 个待触发条目。
 *    loadWindow() 会在任务变更/重启时反复装载全部 WAITING 触发器，若直接 put 而不去重，
 *    同一触发器会累积多个条目 → 到点时被重复/级联触发（曾导致 INTERVAL 在 11:09 一瞬间
 *    连发 4 次、并生成"未来时刻"待办）。故 schedule() 必须按 triggerId 去重替换。
 *
 * 断点建议：在 worker 循环 take() 之后下断，观察精确触发的触发器。
 */
public class TimingWheel {

    private final DelayQueue<DelayedTrigger> queue = new DelayQueue<>();

    /** triggerId -> 当前在轮条目（用于去重替换） */
    private final Map<Long, DelayedTrigger> scheduled = new ConcurrentHashMap<>();

    /** 去重入轮：同一 triggerId 先移除旧条目再加新条目，保证至多 1 条 */
    public void schedule(Long triggerId, long epochMs) {
        DelayedTrigger old = scheduled.remove(triggerId);
        if (old != null) {
            queue.remove(old);
        }
        DelayedTrigger dt = new DelayedTrigger(triggerId, epochMs);
        scheduled.put(triggerId, dt);
        queue.put(dt);
    }

    public DelayedTrigger take() throws InterruptedException {
        DelayedTrigger dt = queue.take();
        // 仅当仍是当前登记项时才移除，避免误删 loadWindow 新放入的条目
        scheduled.remove(dt.getTriggerId(), dt);
        return dt;
    }

    public int size() {
        return queue.size();
    }

    /** 待触发的延迟元素 */
    public static class DelayedTrigger implements Delayed {
        private final Long triggerId;
        private final long epochMs;

        public DelayedTrigger(Long triggerId, long epochMs) {
            this.triggerId = triggerId;
            this.epochMs = epochMs;
        }

        public Long getTriggerId() {
            return triggerId;
        }

        @Override
        public long getDelay(TimeUnit unit) {
            return unit.convert(epochMs - System.currentTimeMillis(), TimeUnit.MILLISECONDS);
        }

        @Override
        public int compareTo(Delayed o) {
            return Long.compare(this.epochMs, ((DelayedTrigger) o).epochMs);
        }
    }
}
