package com.workbuddy.sched.scheduler;

import com.workbuddy.sched.common.TimeZoneUtil;
import com.workbuddy.sched.entity.SchedTrigger;
import com.workbuddy.sched.enums.TriggerStatus;
import com.workbuddy.sched.mapper.SchedTriggerMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import jakarta.annotation.PreDestroy;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 调度引导：启动时间轮 worker + 周期扫描兜底。
 * 断点建议：wheelWorker 循环 take() 之后、dispatch 之前下断，观察精确触发链路。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SchedulerBootstrap {

    private final TriggerEngine triggerEngine;
    private final Dispatcher dispatcher;
    private final ClusterManager clusterManager;
    private final SchedTriggerMapper triggerMapper;

    @Value("${scheduler.dispatch-interval-ms:2000}")
    private long dispatchIntervalMs;

    private final AtomicBoolean running = new AtomicBoolean(false);
    private Thread wheelThread;

    @EventListener(ApplicationReadyEvent.class)
    public void start() {
        if (!running.compareAndSet(false, true)) return;
        wheelThread = new Thread(this::wheelWorker, "sched-wheel-worker");
        wheelThread.setDaemon(true);
        wheelThread.start();
        log.info("scheduler bootstrap started (dispatchIntervalMs={})", dispatchIntervalMs);
    }

    /** 时间轮 worker：到点精确触发；执行后重新入轮（WAITING 且未来才再次触发） */
    private void wheelWorker() {
        TimingWheel wheel = triggerEngine.getWheel();
        while (running.get()) {
            try {
                TimingWheel.DelayedTrigger dt = wheel.take();
                SchedTrigger t = triggerMapper.selectById(dt.getTriggerId());
                if (t == null) continue;
                if (TriggerStatus.WAITING.name().equals(t.getStatus())) {
                    dispatcher.tryAcquireAndRun(t);
                }
                // 重新武装：若执行后仍为 WAITING（已重算下一次），再次入轮
                SchedTrigger after = triggerMapper.selectById(dt.getTriggerId());
                if (after != null && TriggerStatus.WAITING.name().equals(after.getStatus())) {
                    long epoch = after.getNextFireTime().atZone(TimeZoneUtil.ZONE).toInstant().toEpochMilli();
                    if (epoch > System.currentTimeMillis()) {
                        wheel.schedule(after.getTriggerId(), epoch);
                    }
                }
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                log.error("wheel worker error", e);
            }
        }
    }

    /** 周期扫描兜底（与 SKIP LOCKED 配合，覆盖时间轮未命中/节点重启/错过触发）  */
    @Scheduled(fixedDelayString = "${scheduler.dispatch-interval-ms:2000}")
    public void scanTick() {
        if (!running.get()) return;
        // scanDue 在事务提交后返回抢占集合；runClaimed 在事务外逐个执行，避免持锁死锁（R11）
        dispatcher.runClaimed(dispatcher.scanDue(200));
    }

    @PreDestroy
    public void stop() {
        running.set(false);
        if (wheelThread != null) wheelThread.interrupt();
    }
}
