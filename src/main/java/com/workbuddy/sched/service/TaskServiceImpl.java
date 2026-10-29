package com.workbuddy.sched.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.workbuddy.sched.common.BizException;
import com.workbuddy.sched.common.ErrorCode;
import com.workbuddy.sched.common.TimeZoneUtil;
import com.workbuddy.sched.dto.*;
import com.workbuddy.sched.entity.SchedTask;
import com.workbuddy.sched.entity.SchedTrigger;
import com.workbuddy.sched.entity.SchedTodo;
import com.workbuddy.sched.entity.SchedFireLog;
import com.workbuddy.sched.enums.TriggerStatus;
import com.workbuddy.sched.mapper.SchedTaskMapper;
import com.workbuddy.sched.mapper.SchedTriggerMapper;
import com.workbuddy.sched.mapper.SchedTodoMapper;
import com.workbuddy.sched.mapper.SchedFireLogMapper;
import com.workbuddy.sched.scheduler.ComputeNextRun;
import com.workbuddy.sched.scheduler.JobExecutor;
import com.workbuddy.sched.scheduler.RedisTriggerPubSub;
import com.workbuddy.sched.scheduler.SequenceService;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.support.TransactionSynchronizationAdapter;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * 任务服务实现：任务 CRUD + 触发器维护 + Redis 变更广播。
 */
@Service
@RequiredArgsConstructor
public class TaskServiceImpl implements TaskService {

    private final SchedTaskMapper taskMapper;
    private final SchedTriggerMapper triggerMapper;
    private final SequenceService seq;
    private final ComputeNextRun compute;
    private final RedisTriggerPubSub pubSub;
    private final JobExecutor jobExecutor;
    private final SchedTodoMapper todoMapper;
    private final SchedFireLogMapper fireLogMapper;

    @Override
    @Transactional
    public Long create(TaskCreateReq req, String userId) {
        SchedTask task = new SchedTask();
        task.setTaskId(seq.nextTask());
        task.setUserId(userId);
        task.setName(req.getName());
        task.setContent(req.getContent());
        task.setMode(req.getMode().toUpperCase());
        task.setIntervalMin(req.getIntervalMin());
        task.setAtTime(req.getAtTime());
        task.setWeekday(req.getWeekday());
        task.setMonthDay(req.getMonthDay());
        task.setPriority(req.getPriority());
        task.setProject(req.getProject());
        task.setTimezone(TimeZoneUtil.ZONE.getId());
        task.setEnabled("Y");
        task.setCreatedAt(LocalDateTime.now(TimeZoneUtil.ZONE));
        task.setUpdatedAt(task.getCreatedAt());
        taskMapper.insert(task);

        SchedTrigger trigger = new SchedTrigger();
        trigger.setTriggerId(seq.nextTrigger());
        trigger.setTaskId(task.getTaskId());
        trigger.setUserId(userId);
        long nextMs = compute.compute(task, null);
        trigger.setNextFireTime(compute.toLocal(nextMs));
        trigger.setStatus(TriggerStatus.WAITING.name());
        trigger.setUpdatedAt(LocalDateTime.now(TimeZoneUtil.ZONE));
        triggerMapper.insert(trigger);

        publishAfterCommit(userId);
        return task.getTaskId();
    }

    @Override
    @Transactional
    public void update(Long taskId, TaskUpdateReq req, String userId) {
        SchedTask task = taskMapper.selectById(taskId);
        if (task == null || !userId.equals(task.getUserId())) {
            throw new BizException(ErrorCode.TASK_NOT_FOUND, "task not found");
        }
        if (req.getName() != null) task.setName(req.getName());
        if (req.getContent() != null) task.setContent(req.getContent());
        if (req.getMode() != null) task.setMode(req.getMode().toUpperCase());
        if (req.getIntervalMin() != null) task.setIntervalMin(req.getIntervalMin());
        if (req.getAtTime() != null) task.setAtTime(req.getAtTime());
        if (req.getWeekday() != null) task.setWeekday(req.getWeekday());
        if (req.getMonthDay() != null) task.setMonthDay(req.getMonthDay());
        if (req.getPriority() != null) task.setPriority(req.getPriority());
        if (req.getProject() != null) task.setProject(req.getProject());
        if (req.getEnabled() != null) task.setEnabled(req.getEnabled());
        task.setUpdatedAt(LocalDateTime.now(TimeZoneUtil.ZONE));
        taskMapper.updateById(task);

        // 重算触发器（若停用则置 PAUSED；否则重算下次并 WAITING）
        SchedTrigger trigger = triggerMapper.selectOne(
                new LambdaQueryWrapper<SchedTrigger>().eq(SchedTrigger::getTaskId, taskId));
        if (trigger != null) {
            if ("N".equals(task.getEnabled())) {
                trigger.setStatus(TriggerStatus.PAUSED.name());
            } else {
                long nextMs = compute.compute(task, null);
                trigger.setNextFireTime(compute.toLocal(nextMs));
                trigger.setStatus(TriggerStatus.WAITING.name());
            }
            trigger.setUpdatedAt(LocalDateTime.now(TimeZoneUtil.ZONE));
            triggerMapper.updateById(trigger);
        }
        publishAfterCommit(userId);
    }

    @Override
    public IPage<TaskResp> list(TaskQueryReq req, String userId) {
        Page<SchedTask> page = new Page<>(req.getPage(), req.getSize());
        LambdaQueryWrapper<SchedTask> w = new LambdaQueryWrapper<>();
        if (req.getNameLike() != null) w.like(SchedTask::getName, req.getNameLike());
        if (req.getMode() != null) w.eq(SchedTask::getMode, req.getMode().toUpperCase());
        if (req.getEnabled() != null) w.eq(SchedTask::getEnabled, req.getEnabled());
        w.orderByDesc(SchedTask::getCreatedAt);
        IPage<SchedTask> p = taskMapper.selectPage(page, w);

        Page<TaskResp> resp = new Page<>(p.getCurrent(), p.getSize(), p.getTotal());
        resp.setRecords(p.getRecords().stream().map(t -> {
            TaskResp r = new TaskResp();
            r.setTaskId(t.getTaskId());
            r.setName(t.getName());
            r.setContent(t.getContent());
            r.setMode(t.getMode());
            r.setIntervalMin(t.getIntervalMin());
            r.setAtTime(t.getAtTime());
            r.setWeekday(t.getWeekday());
            r.setMonthDay(t.getMonthDay());
            r.setPriority(t.getPriority());
            r.setProject(t.getProject());
            r.setEnabled(t.getEnabled());
            r.setCreatedAt(t.getCreatedAt());
            SchedTrigger tr = triggerMapper.selectOne(
                    new LambdaQueryWrapper<SchedTrigger>().eq(SchedTrigger::getTaskId, t.getTaskId()));
            if (tr != null) r.setNextFireTime(tr.getNextFireTime());
            return r;
        }).toList());
        return resp;
    }

    @Override
    @Transactional
    public void setEnabled(Long taskId, boolean enabled, String userId) {
        TaskUpdateReq u = new TaskUpdateReq();
        u.setEnabled(enabled ? "Y" : "N");
        update(taskId, u, userId);
    }

    @Override
    @Transactional
    public void delete(Long taskId, String userId) {
        SchedTask task = taskMapper.selectById(taskId);
        if (task == null || !userId.equals(task.getUserId())) {
            throw new BizException(ErrorCode.TASK_NOT_FOUND, "task not found");
        }
        // 级联清理子表，避免任务删除后 SCHED_TODO / SCHED_FIRE_LOG 残留为孤儿（无 UI/API 删除入口）。
        // 删除顺序必须：先子表(执行日志/待办) → 触发器 → 父表任务，
        // 否则 FK(NO ACTION) 会触发 ORA-02292（子记录存在）导致事务回滚/500。
        fireLogMapper.delete(new LambdaQueryWrapper<SchedFireLog>().eq(SchedFireLog::getTaskId, taskId));
        todoMapper.delete(new LambdaQueryWrapper<SchedTodo>().eq(SchedTodo::getSourceTaskId, taskId));
        triggerMapper.delete(new LambdaQueryWrapper<SchedTrigger>().eq(SchedTrigger::getTaskId, taskId));
        taskMapper.deleteById(taskId);
        publishAfterCommit(userId);
    }

    @Override
    public void fire(Long taskId, String userId) {
        SchedTask task = taskMapper.selectById(taskId);
        if (task == null || !userId.equals(task.getUserId())) {
            throw new BizException(ErrorCode.TASK_NOT_FOUND, "task not found");
        }
        SchedTrigger trigger = triggerMapper.selectOne(
                new LambdaQueryWrapper<SchedTrigger>().eq(SchedTrigger::getTaskId, taskId));
        if (trigger == null) {
            // 触发器缺失（异常态）：即时重建一个 WAITING 触发器，保证「立即执行」仍可调试。
            // 注意：本方法不加 @Transactional，使触发器插入即时提交，
            // 以便 jobExecutor.execute（REQUIRES_NEW）内对触发器的引用（如 SCHED_FIRE_LOG 外键）可见。
            trigger = new SchedTrigger();
            trigger.setTriggerId(seq.nextTrigger());
            trigger.setTaskId(task.getTaskId());
            trigger.setUserId(userId);
            long nextMs = compute.compute(task, null);
            trigger.setNextFireTime(compute.toLocal(nextMs));
            trigger.setStatus(TriggerStatus.WAITING.name());
            trigger.setUpdatedAt(LocalDateTime.now(TimeZoneUtil.ZONE));
            triggerMapper.insert(trigger);
        }
        // 复用调度内核的 JobExecutor（独立 REQUIRES_NEW 事务：生成待办 + 写执行日志 + 重算下次），
        // 与定时触发走完全一致的逻辑，避免「立即执行」与「自动触发」行为分叉。
        // 关键：手动触发以"当前时间"作为本次 fire 的 scheduledFireTime，规避
        // SCHED_FIRE_LOG.UK_FIRE_TASK_TIME(TASK_ID, SCHEDULED_FIRE_TIME) 唯一冲突（ORA-00001 回滚），
        // 同时 JobExecutor 内部仍按触发器原 nextFireTime 重算下一次，调度节奏不被平移。
        jobExecutor.execute(trigger, LocalDateTime.now(TimeZoneUtil.ZONE));
    }

    /**
     * 在 DB 事务「提交后」再广播 Redis 变更（事务外执行 Redis I/O）。
     * 关键修复：避免在打开的 DB 事务内执行 Redis I/O —— 否则一旦 Redis 连接池耗尽/阻塞，
     * 线程会带着 DB 行锁空转，进而卡死调度（见 R10 / 联调测试计划）。
     * 若当前无事务则即时广播，保证语义不变。
     */
    private void publishAfterCommit(String userId) {
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            TransactionSynchronizationManager.registerSynchronization(
                    new TransactionSynchronizationAdapter() {
                        @Override
                        public void afterCommit() {
                            pubSub.publishTaskChange(userId);
                        }
                    });
        } else {
            pubSub.publishTaskChange(userId);
        }
    }
}
