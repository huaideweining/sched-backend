package com.workbuddy.sched.scheduler;

import com.workbuddy.sched.common.TimeZoneUtil;
import com.workbuddy.sched.config.SchedulerConfig;
import com.workbuddy.sched.entity.SchedNode;
import com.workbuddy.sched.mapper.SchedNodeMapper;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.net.InetAddress;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 集群管理：节点注册 / 心跳 / 分片 / 选主（基于 SCHED_NODE）。
 * 断点建议：在 heartbeat() 与 isLeader() 处下断，观察多节点角色分配。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ClusterManager {

    private final SchedNodeMapper nodeMapper;
    private final SchedulerConfig schedulerConfig;

    @Value("${scheduler.node-id:node-1}")
    private String nodeId;

    @Getter
    private int shardTotal;
    @Getter
    private int shardMod;
    @Getter
    private volatile boolean leader;

    @PostConstruct
    public void register() {
        shardTotal = Math.max(1, schedulerConfig.getShardTotalValue());
        try {
            String host = InetAddress.getLocalHost().getHostName();
            SchedNode node = new SchedNode();
            node.setNodeId(nodeId);
            node.setHost(host);
            node.setShardTotal(shardTotal);
            node.setShardMod(Math.floorMod(nodeId.hashCode(), shardTotal));
            node.setLastHeartbeat(LocalDateTime.now(TimeZoneUtil.ZONE));
            node.setStatus("UP");
            node.setIsLeader("N");
            nodeMapper.insert(node);
        } catch (DuplicateKeyException e) {
            // 节点已存在（重启场景）：幂等刷新心跳与状态，消除 ORA-00001 噪声
            SchedNode node = new SchedNode();
            node.setNodeId(nodeId);
            node.setLastHeartbeat(LocalDateTime.now(TimeZoneUtil.ZONE));
            node.setStatus("UP");
            nodeMapper.updateById(node);
            log.info("node register upsert (already exists): {}", nodeId);
        } catch (Exception e) {
            log.warn("node register failed: {}", e.getMessage());
        }
        shardMod = Math.floorMod(nodeId.hashCode(), shardTotal);
        refreshLeader();
    }

    @Scheduled(fixedDelay = 10000)
    public void heartbeat() {
        SchedNode n = new SchedNode();
        n.setNodeId(nodeId);
        n.setLastHeartbeat(LocalDateTime.now(TimeZoneUtil.ZONE));
        n.setStatus("UP");
        try {
            nodeMapper.updateById(patchHeartbeat(n));
        } catch (Exception e) {
            log.debug("heartbeat upsert bypass: {}", e.getMessage());
        }
        refreshLeader();
    }

    private SchedNode patchHeartbeat(SchedNode n) {
        // 仅更新心跳与状态（MyBatis-Plus updateById 会更新非空字段）
        return n;
    }

    /** 选主：在心跳正常的节点中，取 nodeId 字典序最小者为 Leader（主节点负责全量装载/补算） */
    private void refreshLeader() {
        try {
            List<SchedNode> nodes = nodeMapper.selectList(
                    com.baomidou.mybatisplus.core.toolkit.Wrappers.<SchedNode>lambdaQuery()
                            .eq(SchedNode::getStatus, "UP"));
            leader = !nodes.isEmpty() && nodes.stream()
                    .map(SchedNode::getNodeId).min(String::compareTo).orElse("").equals(nodeId);
        } catch (Exception e) {
            leader = true; // 单节点降级
        }
    }
}
