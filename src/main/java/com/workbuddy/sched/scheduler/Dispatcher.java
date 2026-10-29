package com.workbuddy.sched.scheduler;

import com.workbuddy.sched.common.TimeZoneUtil;
import com.workbuddy.sched.entity.SchedTrigger;
import com.workbuddy.sched.enums.TriggerStatus;
import com.workbuddy.sched.mapper.SchedTriggerMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 调度分发器。两条互不冲突的抢占路径：
 * 1) scanDue()：周期性 DB 轮询兜底（FOR UPDATE SKIP LOCKED 批量抢占到期触发器）
 * 2) tryAcquireAndRun()：时间轮到点后的单条 CAS 抢占（WAITING→ACQUIRED）
 * 两者均保证：同一触发器同一时刻只被一个节点执行一次。
 * 断点建议：在 scanDue 的 claimDue 返回后、execute 前下断，观察抢到的触发器集合。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class Dispatcher {

    private final SchedTriggerMapper triggerMapper;
    private final JobExecutor jobExecutor;
    private final ClusterManager clusterManager;

    /**
     * 周期性扫描（Dispatcher 定时线程调用）。
     * 重要：本方法在 @Transactional 内只做「抢占 + 置 ACQUIRED」，提交后即释放行锁；
     * 真正的执行交给 {@link #runClaimed} 在事务外调用 JobExecutor（REQUIRES_NEW），
     * 避免「scanDue 持锁 + execute 更新同一行」造成的死锁（见 R11 / 联调测试计划）。
     * 返回本次抢占到的触发器集合。
     */
    @Transactional
    public List<SchedTrigger> scanDue(int limit) {
        LocalDateTime now = LocalDateTime.now(TimeZoneUtil.ZONE);
        // SKIP LOCKED：多节点并发也只一个节点拿到同一行
        List<SchedTrigger> claimed = triggerMapper.claimDue(
                TriggerStatus.WAITING.name(),
                now,
                clusterManager.getShardTotal(),
                clusterManager.getShardMod(),
                limit);
        for (SchedTrigger t : claimed) {
            t.setStatus(TriggerStatus.ACQUIRED.name());
            triggerMapper.updateById(t);   // 锁定态，防止被时间轮再次取走
        }
        if (!claimed.isEmpty()) {
            log.debug("scanDue claimed={}", claimed.size());
        }
        return claimed;
    }

    /**
     * 在 scanDue 事务提交后调用：逐个触发，每个触发独立 REQUIRES_NEW 事务，互不拖累。
     * 本方法本身无事务，因此 JobExecutor 更新触发器行时不会与 scanDue 的持锁冲突（R11 修复）。
     */
    public void runClaimed(List<SchedTrigger> claimed) {
        for (SchedTrigger t : claimed) {
            jobExecutor.execute(t);
        }
    }

    /** 时间轮到点后的单条抢占；返回是否成功抢到并执行 */
    public boolean tryAcquireAndRun(SchedTrigger trigger) {
        int n = triggerMapper.compareAndSet(trigger.getTriggerId(),
                TriggerStatus.WAITING.name(), TriggerStatus.ACQUIRED.name());
        if (n == 0) {
            return false; // 已被其它路径/节点抢走
        }
        jobExecutor.execute(trigger);
        return true;
    }
}
