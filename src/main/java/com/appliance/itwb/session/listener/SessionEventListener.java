package com.appliance.itwb.session.listener;

import com.appliance.itwb.session.listener.event.SessionKickedEvent;
import org.springframework.context.ApplicationListener;

/**
 * 会话变更监听器接口（预留推送能力）。
 *
 * <p><b>文件作用</b>：给用户/前端提供"被踢下线"的下游出口。工程内置一个只打日志的实现，
 * 业务侧可自行增加实现，把事件转成企业微信消息、SSE 推送或 WebSocket 广播。</p>
 *
 * <p><b>依赖</b>：spring-context（ApplicationListener）。</p>
 *
 * <p><b>Oracle 注意点</b>：无。</p>
 *
 * <p><b>如何对接</b>：</p>
 * <ul>
 *   <li>SSE：在实现类里维护 {@code Map&lt;String, SseEmitter&gt;}，key 用 userId+sessionId；
 *       事件到达时 {@code emitter.send(event)}，并在 finally 中 unregister；</li>
 *   <li>WebSocket：注入 {@code SimpMessagingTemplate}，推送到
 *       {@code /user/queue/session/} + userId；</li>
 *   <li>企业微信机器人：按 actionType 拼卡片消息，只取 userId/deviceType/remark 三个字段；</li>
 *   <li>异步执行：实现类建议加 {@code @Async} 并用线程池隔离，避免推送卡住主流程；</li>
 *   <li>事务边界：推荐在监听方法上标注
 *       {@code @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)}，
 *       确保只有在数据库提交成功后才通知对端，避免"通知到了但会话还在库里"的错乱。</li>
 * </ul>
 *
 * <p><b>说明</b>：本接口目前没有任何阻塞式实现，属于"预留但可用"的扩展点。</p>
 */
public interface SessionEventListener extends ApplicationListener<SessionKickedEvent> {
}
