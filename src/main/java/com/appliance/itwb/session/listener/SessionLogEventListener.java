package com.appliance.itwb.session.listener;

import com.appliance.itwb.session.listener.event.SessionKickedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 内置会话变更监听器的最小可用实现：只写日志，不做任何业务处理。
 *
 * <p><b>文件作用</b>：验证监听器链路是否通畅（配置错、事件没发出去时可一眼看出），
 * 同时为"踢下线"这类敏感动作留一份可检索的服务端痕迹。</p>
 *
 * <p><b>依赖</b>：slf4j、spring-context（作为 ApplicationListener Bean 被自动识别）。</p>
 *
 * <p><b>Oracle 注意点</b>：无。</p>
 *
 * <p><b>日志脱敏</b>：只打印 userId / sessionId / deviceType / ip，不打印令牌与哈希。</p>
 */
@Component
public class SessionLogEventListener implements SessionEventListener {

    private static final Logger log = LoggerFactory.getLogger(SessionLogEventListener.class);

    @Override
    public void onApplicationEvent(SessionKickedEvent event) {
        log.info("[session-event] action={}, sessionId={}, userId={}, deviceType={}, ip={}, "
                        + "operator={}, remark={}, occurTime={}",
                event.getActionType(), event.getSessionId(), event.getUserId(),
                event.getDeviceType(), event.getIp(), event.getOperatorId(),
                event.getRemark(), event.getOccurTime());
    }
}
