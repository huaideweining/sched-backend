package com.workbuddy.sched.scheduler;

import com.workbuddy.sched.common.Constants;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.data.redis.listener.Topic;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;

/**
 * 任务变更 Pub/Sub：任务 CRUD 后 publish(userId)，各节点 TriggerEngine 实时重载。
 * 断点建议：publish() 与 TriggerEngine.onMessage() 处下断，观察跨节点实时通知。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RedisTriggerPubSub {

    private final RedisTemplate<String, Object> redisTemplate;
    private final RedisMessageListenerContainer container;
    private final TriggerEngine triggerEngine;

    private final Topic topic = new ChannelTopic(Constants.REDIS_TOPIC_TASK_CHANGE);

    @PostConstruct
    public void init() {
        container.addMessageListener(triggerEngine, topic);
    }

    public void publishTaskChange(String userId) {
        try {
            redisTemplate.convertAndSend(Constants.REDIS_TOPIC_TASK_CHANGE, userId);
        } catch (Exception e) {
            log.warn("publish task change failed: {}", e.getMessage());
        }
    }
}
