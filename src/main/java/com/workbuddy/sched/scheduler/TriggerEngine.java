package com.workbuddy.sched.scheduler;

import com.workbuddy.sched.common.Constants;
import com.workbuddy.sched.common.TimeZoneUtil;
import com.workbuddy.sched.entity.SchedTrigger;
import com.workbuddy.sched.mapper.SchedTriggerMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

/**
 * 触发器引擎：把到期触发器装入时间轮；监听 Redis 变更事件实时重载。
 * 断点建议：loadInitial() 与 onTaskChange() 处下断，观察触发器入轮。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TriggerEngine implements MessageListener {

    private final SchedTriggerMapper triggerMapper;
    private final ClusterManager clusterManager;
    private final TimingWheel wheel = new TimingWheel();
    private final int LOAD_WINDOW_MIN = 10; // 仅装载未来 10 分钟内的触发器到时间轮

    @PostConstruct
    public void loadInitial() {
        loadWindow();
    }

    /** 装载本分片、未来窗口内到期的 WAITING 触发器到时间轮 */
    public void loadWindow() {
        LocalDateTime now = LocalDateTime.now(TimeZoneUtil.ZONE);
        LocalDateTime horizon = now.plusMinutes(LOAD_WINDOW_MIN);
        List<SchedTrigger> list = triggerMapper.selectList(
                com.baomidou.mybatisplus.core.toolkit.Wrappers.<SchedTrigger>lambdaQuery()
                        .eq(SchedTrigger::getStatus, "WAITING")
                        .le(SchedTrigger::getNextFireTime, horizon));
        for (SchedTrigger t : list) {
            if (Math.floorMod(t.getUserId().hashCode(), clusterManager.getShardTotal()) != clusterManager.getShardMod()) {
                continue; // 仅本分片
            }
            wheel.schedule(t.getTriggerId(), t.getNextFireTime().atZone(TimeZoneUtil.ZONE).toInstant().toEpochMilli());
        }
        log.info("trigger engine loaded={} into wheel", list.size());
    }

    /** Redis 订阅回调：任务变更后重载该用户触发器 */
    @Override
    public void onMessage(Message message, byte[] pattern) {
        String payload = new String(message.getBody());
        log.info("task change event: {}", payload);
        loadWindow(); // 简化：全量重载窗口（也可按 userId 精确重载）
    }

    public TimingWheel getWheel() {
        return wheel;
    }
}
