package com.appliance.itwb.session.listener;

import com.appliance.itwb.session.listener.event.SessionKickedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * 会话事件发布器。
 *
 * <p><b>文件作用</b>：Service 层只依赖这个发布器，不关心下游是日志、SSE 还是企业微信。
 * 事件发布是同步的，但下游实现类可以自己加 {@code @Async} 或消息队列削峰。</p>
 *
 * <p><b>依赖</b>：spring-context（ApplicationEventPublisher）。</p>
 *
 * <p><b>Oracle 注意点</b>：无。</p>
 *
 * <p><b>为什么在 Service 里同步发布而不是放在过滤器里</b>：
 * 踢下线的动作由登录/踢人接口触发，事件只有在该接口的事务提交后才有意义，
 * 下游实现类建议用 {@code @TransactionalEventListener(AFTER_COMMIT)} 订阅。</p>
 */
@Component
public class SessionEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(SessionEventPublisher.class);

    private final ApplicationEventPublisher applicationEventPublisher;

    /**
     * 构造事件发布器。
     *
     * @param applicationEventPublisher Spring 事件发布器
     */
    public SessionEventPublisher(ApplicationEventPublisher applicationEventPublisher) {
        this.applicationEventPublisher = applicationEventPublisher;
    }

    /**
     * 发布会话变更事件。
     *
     * @param event 会话变更事件
     */
    public void publishSessionKicked(SessionKickedEvent event) {
        applicationEventPublisher.publishEvent(event);
    }

    /**
     * 发布"新登录导致旧会话被顶替"事件。
     *
     * @param sessionId   被顶替的旧会话号
     * @param userId      用户主键
     * @param username    登录名
     * @param deviceType  终端类型
     * @param deviceInfo  终端描述
     * @param ip          登录 IP
     * @param operatorId  操作人（本次登录用户）
     */
    public void publishReplaced(String sessionId, String userId, String username,
                                String deviceType, String deviceInfo, String ip, String operatorId) {
        SessionKickedEvent event = new SessionKickedEvent(this, "REPLACED", sessionId, userId, username,
                deviceType, deviceInfo, ip, operatorId, "同设备新登录，旧会话被顶替", LocalDateTime.now());
        publishSessionKicked(event);
    }

    /**
     * 发布"被踢下线"事件。
     *
     * @param sessionId   会话号
     * @param userId      用户主键
     * @param username    登录名
     * @param deviceType  终端类型
     * @param deviceInfo  终端描述
     * @param ip          登录 IP
     * @param operatorId  操作人
     * @param remark      踢出原因
     */
    public void publishKicked(String sessionId, String userId, String username,
                              String deviceType, String deviceInfo, String ip,
                              String operatorId, String remark) {
        SessionKickedEvent event = new SessionKickedEvent(this, "KICK", sessionId, userId, username,
                deviceType, deviceInfo, ip, operatorId, remark, LocalDateTime.now());
        publishSessionKicked(event);
    }

    /**
     * 发布"主动登出"事件。
     *
     * @param sessionId 会话号
     * @param userId    用户主键
     * @param username  登录名
     * @param ip        登录 IP
     */
    public void publishLogout(String sessionId, String userId, String username, String ip) {
        SessionKickedEvent event = new SessionKickedEvent(this, "LOGOUT", sessionId, userId, username,
                null, null, ip, userId, "用户主动登出", LocalDateTime.now());
        publishSessionKicked(event);
    }
}
