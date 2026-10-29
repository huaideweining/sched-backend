package com.workbuddy.sched.scheduler;

import com.workbuddy.sched.common.TimeZoneUtil;
import com.workbuddy.sched.entity.SchedTrigger;
import com.workbuddy.sched.mapper.SchedTriggerMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 错过触发监控：定期统计「已 overdue 且仍 WAITING」的触发器（节点宕机遗留）。
 * 实际补算由 Dispatcher.scanDue（抓取 NEXT_FIRE_TIME<=now）兜底完成；
 * 此处仅做可观测性与告警计数（防死循环策略在 JobExecutor 重算时向前推进，不回溯补发）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MisfireRecovery {

    private final SchedTriggerMapper triggerMapper;
    private final ClusterManager clusterManager;

    @Scheduled(fixedDelay = 60000)
    public void reportOverdue() {
        LocalDateTime now = LocalDateTime.now(TimeZoneUtil.ZONE);
        List<SchedTrigger> overdue = triggerMapper.selectList(
                com.baomidou.mybatisplus.core.toolkit.Wrappers.<SchedTrigger>lambdaQuery()
                        .eq(SchedTrigger::getStatus, "WAITING")
                        .lt(SchedTrigger::getNextFireTime, now));
        if (!overdue.isEmpty()) {
            long mine = overdue.stream()
                    .filter(t -> Math.floorMod(t.getUserId().hashCode(), clusterManager.getShardTotal()) == clusterManager.getShardMod())
                    .count();
            log.warn("overdue WAITING triggers total={} mine={} (will be reclaimed by scanDue)", overdue.size(), mine);
        }
    }
}
