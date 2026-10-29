package com.workbuddy.sched.scheduler;

import com.workbuddy.sched.common.TenantContext;
import com.workbuddy.sched.common.TimeZoneUtil;
import com.workbuddy.sched.config.SchedulerConfig;
import com.workbuddy.sched.entity.*;
import com.workbuddy.sched.enums.TriggerStatus;
import com.workbuddy.sched.mapper.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Job 执行器：生成待办 + 写执行日志（幂等锚点）+ 重算下一次触发。
 * 每个任务独立事务（REQUIRES_NEW），避免单任务失败拖累整批。
 * 断点建议：在 execute() 内「生成待办」与「重算 nextFireTime」处下断，观察端到端流程。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class JobExecutor {

    private final ComputeNextRun compute;
    private final SchedulerConfig schedulerConfig;
    private final SequenceService seq;
    private final SchedTaskMapper taskMapper;
    private final SchedTriggerMapper triggerMapper;
    private final SchedTodoMapper todoMapper;
    private final SchedFireLogMapper fireLogMapper;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void execute(SchedTrigger trigger) {
        // 自动调度入口：scheduledFireTime 取触发器原定时间（与历史行为一致），
        // 保证 SCHED_FIRE_LOG.UK_FIRE_TASK_TIME(TASK_ID, SCHEDULED_FIRE_TIME) 不冲突（每次推进到新值）。
        doExecute(trigger, null);
    }

    /**
     * 手动「立即执行」入口：以"当前时间"作为本次 fire 的 scheduledFireTime，
     * 避免与 SCHED_FIRE_LOG 上已有 (TASK_ID, SCHEDULED_FIRE_TIME) 行冲突导致 ORA-00001 回滚；
     * 同时保留触发器原 nextFireTime 不变（调度节奏不被手动触发平移，自动触发仍按原计划推进）。
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void execute(SchedTrigger trigger, LocalDateTime scheduledFireTimeOverride) {
        doExecute(trigger, scheduledFireTimeOverride);
    }

    /**
     * 执行核心：生成待办 + 写执行日志（幂等锚点）+ 重算下一次触发。
     * 每个任务独立事务（REQUIRES_NEW），避免单任务失败拖累整批。
     * scheduledFireTimeOverride 为非 null 时（手动触发），fire log 与 todo 的 scheduledFireTime
     * 用它（= 当前时间，必唯一）；为 null 时（自动触发）沿用触发器原 nextFireTime。
     * 注意：触发器下一次时间的重算始终基于触发器自身的 nextFireTime，与 override 无关。
     */
    private void doExecute(SchedTrigger trigger, LocalDateTime scheduledFireTimeOverride) {
        // 设置租户上下文：本任务归属用户，保证 TASK/TODO 行级隔离且非空
        TenantContext.set(trigger.getUserId());
        try {
            // 手动触发用"当前时间"作本次执行时间；自动触发用触发器原定时间
            LocalDateTime scheduledFireTime = (scheduledFireTimeOverride != null)
                    ? scheduledFireTimeOverride : trigger.getNextFireTime();
            SchedFireLog flog = new SchedFireLog();
            flog.setFireId(seq.nextFire());
            flog.setTaskId(trigger.getTaskId());
            flog.setTriggerId(trigger.getTriggerId());
            flog.setUserId(trigger.getUserId());
            flog.setScheduledFireTime(scheduledFireTime);
            flog.setActualFireTime(LocalDateTime.now(TimeZoneUtil.ZONE));
            try {
                SchedTask task = taskMapper.selectById(trigger.getTaskId());
                if (task == null) {
                    flog.setStatus("FAILED");
                    flog.setErrorMsg("task not found: " + trigger.getTaskId());
                    trigger.setStatus(TriggerStatus.ERROR.name());
                    triggerMapper.updateById(trigger);
                    fireLogMapper.insert(flog);
                    return;
                }

                // 1) 生成待办（标题带时间戳，避免与历史标题冲突）
                String stamp = compute.stamp(epochMsFromLocal(scheduledFireTime));
                String title = (task.getContent() != null ? task.getContent() : task.getName())
                        + " · " + stamp;
                SchedTodo todo = new SchedTodo();
                todo.setTodoId(seq.nextTodo());
                todo.setUserId(trigger.getUserId());
                todo.setTitle(title);
                todo.setPriority(task.getPriority());
                todo.setProject(task.getProject());
                todo.setDone("N");
                todo.setSourceTaskId(task.getTaskId());
                todo.setScheduledFireTime(scheduledFireTime);
                todo.setCreatedDate(LocalDate.now(TimeZoneUtil.ZONE));
                todoMapper.insert(todo);

                // 2) 重算下一次触发（基于触发器原 nextFireTime，保持调度节奏不变；含 misfire 处理）
                long nowMs = nowEpochMs();
                long nextMs = compute.compute(task, epochMsFromLocal(trigger.getNextFireTime()));
                int cap = Math.max(1, schedulerConfig.getCatchUpMax());
                while (nextMs <= nowMs && cap-- > 0) {
                    nextMs = compute.compute(task, nextMs);
                }
                if (nextMs <= nowMs) {
                    // 仍过期（大跨度错过）：直接以 now 为基准恢复一次，跳过错过的多次
                    nextMs = compute.compute(task, nowMs);
                }
                trigger.setPrevFireTime(trigger.getNextFireTime());
                trigger.setNextFireTime(compute.toLocal(nextMs));
                trigger.setStatus(TriggerStatus.WAITING.name());
                triggerMapper.updateById(trigger);

                flog.setTodoId(todo.getTodoId());
                flog.setStatus("SUCCESS");
                fireLogMapper.insert(flog);
                log.debug("fired task={} scheduled={} todo={}", task.getTaskId(), scheduledFireTime, todo.getTodoId());
            } catch (Exception e) {
                flog.setStatus("FAILED");
                flog.setErrorMsg(truncate(e.getMessage()));
                trigger.setStatus(TriggerStatus.ERROR.name());
                triggerMapper.updateById(trigger);
                fireLogMapper.insert(flog);
                log.error("fire failed task={}", trigger.getTaskId(), e);
            }
        } finally {
            TenantContext.clear();
        }
    }

    /** 统一时区 LocalDateTime -> epoch 毫秒（与 ComputeNextRun.toLocal 严格互逆） */
    private long epochMsFromLocal(LocalDateTime ldt) {
        return ldt.atZone(TimeZoneUtil.ZONE).toInstant().toEpochMilli();
    }

    /** 当前时刻 epoch 毫秒（统一时区 Asia/Shanghai） */
    private long nowEpochMs() {
        return LocalDateTime.now(TimeZoneUtil.ZONE).atZone(TimeZoneUtil.ZONE).toInstant().toEpochMilli();
    }

    private String truncate(String s) {
        if (s == null) return null;
        return s.length() > 2000 ? s.substring(0, 2000) : s;
    }
}
