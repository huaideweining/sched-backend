package com.appliance.itwb.session.listener.event;

import org.springframework.context.ApplicationEvent;

import java.time.LocalDateTime;

/**
 * 会话变更事件。
 *
 * <p><b>文件作用</b>：把"会话被踢/被顶替"这件事广播出去，供其他端感知。
 * 这是**预留的推送钩子**，本工程内只有一个日志监听器，不参与任何业务判定。</p>
 *
 * <p><b>依赖</b>：spring-context（{@link ApplicationEvent}）。</p>
 *
 * <p><b>为什么继承 ApplicationEvent 而不是只实现 Serializable</b>：
 * {@link org.springframework.context.ApplicationListener} 要求事件类型是其子类型，
 * 否则泛型会在编译期报错；继承 ApplicationEvent 后，Spring 才能把它当作
 * 监听器回调的参数类型，也才能配合 @TransactionalEventListener 做提交后通知。</p>
 *
 * <p><b>Oracle 注意点</b>：无。</p>
 *
 * <p><b>对接建议（本类本身不涉及，供接入方参考）</b>：</p>
 * <ul>
 *   <li>企业微信/钉钉：把下面几个字段填进消息卡片，^踢下线^ 由服务端主动推送，用户无需刷新页面；</li>
 *   <li>前端 SSE：服务端为每个在线会话维护一个 SseEmitter，事件到达时 emitter.send(event)；</li>
 *   <li>WebSocket：按 userId 建连接，事件到达时通过 SimpMessagingTemplate 推送到 /user/queue/session；</li>
 *   <li>短轮询：列表接口 GET /api/auth/sessions 已具备同样的信息，只是不够实时。</li>
 * </ul>
 *
 * <p><b>注意</b>：事件里只放展示字段，绝不放令牌哈希。</p>
 *
 * <p><b>source 传什么</b>：统一传事件发布者实例（见 SessionEventPublisher 里的
 * {@code new SessionKickedEvent(this, ...)}）。source 只用于排查"谁发的这个事件"，
 * 不参与任何业务判定，下游不要依赖它。</p>
 */
public class SessionKickedEvent extends ApplicationEvent {

    private static final long serialVersionUID = 1L;

    /**
     * 动作类型：
     * <ul>
     *   <li>LOGIN：新登录导致旧会话被顶替；</li>
     *   <li>LOGOUT：正常登出；</li>
     *   <li>KICK：用户手动踢下线；</li>
     *   <li>ADMIN_KICK：管理端强制下线；</li>
     *   <li>OVER_LIMIT：超出最大会话数被淘汰。</li>
     * </ul>
     */
    private String actionType;

    /** 会话号。 */
    private String sessionId;

    /** 用户主键。 */
    private String userId;

    /** 登录名。 */
    private String username;

    /** 终端类型。 */
    private String deviceType;

    /** 终端描述。 */
    private String deviceInfo;

    /** 登录 IP。 */
    private String ip;

    /** 操作人用户主键，系统动作为空串。 */
    private String operatorId;

    /** 备注或踢出原因。 */
    private String remark;

    /** 事件发生时间。 */
    private LocalDateTime occurTime;

    /**
     * 构造会话变更事件。
     *
     * @param source     事件来源（统一传事件发布者实例）
     * @param actionType 动作类型
     * @param sessionId 会话号
     * @param userId 用户主键
     * @param username 登录名
     * @param deviceType 终端类型
     * @param deviceInfo 终端描述
     * @param ip 登录 IP
     * @param operatorId 操作人
     * @param remark 备注
     * @param occurTime 发生时间
     */
    public SessionKickedEvent(Object source, String actionType, String sessionId, String userId,
                              String username, String deviceType, String deviceInfo, String ip,
                              String operatorId, String remark, LocalDateTime occurTime) {
        super(source);
        this.actionType = actionType;
        this.sessionId = sessionId;
        this.userId = userId;
        this.username = username;
        this.deviceType = deviceType;
        this.deviceInfo = deviceInfo;
        this.ip = ip;
        this.operatorId = operatorId;
        this.remark = remark;
        this.occurTime = occurTime;
    }

    public String getActionType() {
        return actionType;
    }

    public void setActionType(String actionType) {
        this.actionType = actionType;
    }

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getDeviceType() {
        return deviceType;
    }

    public void setDeviceType(String deviceType) {
        this.deviceType = deviceType;
    }

    public String getDeviceInfo() {
        return deviceInfo;
    }

    public void setDeviceInfo(String deviceInfo) {
        this.deviceInfo = deviceInfo;
    }

    public String getIp() {
        return ip;
    }

    public void setIp(String ip) {
        this.ip = ip;
    }

    public String getOperatorId() {
        return operatorId;
    }

    public void setOperatorId(String operatorId) {
        this.operatorId = operatorId;
    }

    public String getRemark() {
        return remark;
    }

    public void setRemark(String remark) {
        this.remark = remark;
    }

    public LocalDateTime getOccurTime() {
        return occurTime;
    }

    public void setOccurTime(LocalDateTime occurTime) {
        this.occurTime = occurTime;
    }
}
