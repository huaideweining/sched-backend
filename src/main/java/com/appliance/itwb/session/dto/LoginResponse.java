package com.appliance.itwb.session.dto;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 登录响应体。
 *
 * <p>接口：POST /api/auth/login</p>
 *
 * <p><b>文件作用</b>：把令牌、会话号、身份与"当前有多少台在线终端"一起返回，
 * 前端可据此决定是否提示"你已在其他设备登录"。</p>
 *
 * <p><b>依赖</b>：无。</p>
 *
 * <p><b>Oracle 注意点</b>：时间字段来自数据库 SYSTIMESTAMP，前端若做倒计时，
 * 请以本响应里的 expireTime 剩余时间为准，避免时钟漂移。</p>
 *
 * <p><b>关于 refreshToken</b>：本实现不做独立的 refresh token 轮换机制。
 * 需要长期静默续期时，前端在剩余有效期低于续期阈值时调用
 * POST /api/auth/session/extend 即可，语义等价于 refresh，实现更小、状态更可控。</p>
 *
 * <p><b>示例</b>：</p>
 * <pre>
 * {
 *   "token": "eyJhbGci...",      // 明文令牌，只给前端，服务端只有哈希
 *   "tokenType": "Bearer",
 *   "sessionId": "sess_9f2c...",
 *   "userId": "u_10001",
 *   "username": "zhangsan",
 *   "deviceType": "PC",
 *   "loginTime": "...",
 *   "expireTime": "...",
 *   "multiLoginEnabled": true,
 *   "maxSessions": 5,
 *   "activeSessions": 2
 * }
 * </pre>
 */
public class LoginResponse implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 明文令牌，前端后续请求必须携带（Authorization: Bearer {token}）。 */
    private String token;

    /** 令牌类型，固定 Bearer。 */
    private String tokenType = "Bearer";

    /** 业务会话号，用于在线终端列表与踢下线。 */
    private String sessionId;

    /** 用户主键。 */
    private String userId;

    /** 登录名。 */
    private String username;

    /** 展示用姓名。 */
    private String displayName;

    /** 终端类型。 */
    private String deviceType;

    /** 登录时间。 */
    private LocalDateTime loginTime;

    /** 过期时间（绝对边界）。 */
    private LocalDateTime expireTime;

    /** 本次登录是否允许多端并存。 */
    private Boolean multiLoginEnabled;

    /** 单用户最大会话数配置，0 表示不限制。 */
    private Integer maxSessions;

    /** 该用户当前有效会话数（含本次）。 */
    private Integer activeSessions;

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public String getTokenType() {
        return tokenType;
    }

    public void setTokenType(String tokenType) {
        this.tokenType = tokenType;
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

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public String getDeviceType() {
        return deviceType;
    }

    public void setDeviceType(String deviceType) {
        this.deviceType = deviceType;
    }

    public LocalDateTime getLoginTime() {
        return loginTime;
    }

    public void setLoginTime(LocalDateTime loginTime) {
        this.loginTime = loginTime;
    }

    public LocalDateTime getExpireTime() {
        return expireTime;
    }

    public void setExpireTime(LocalDateTime expireTime) {
        this.expireTime = expireTime;
    }

    public Boolean getMultiLoginEnabled() {
        return multiLoginEnabled;
    }

    public void setMultiLoginEnabled(Boolean multiLoginEnabled) {
        this.multiLoginEnabled = multiLoginEnabled;
    }

    public Integer getMaxSessions() {
        return maxSessions;
    }

    public void setMaxSessions(Integer maxSessions) {
        this.maxSessions = maxSessions;
    }

    public Integer getActiveSessions() {
        return activeSessions;
    }

    public void setActiveSessions(Integer activeSessions) {
        this.activeSessions = activeSessions;
    }
}
