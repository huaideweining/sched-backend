package com.appliance.itwb.session.dto;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 在线终端视图对象。
 *
 * <p>接口：GET /api/auth/sessions</p>
 *
 * <p><b>文件作用</b>：列表对外脱敏展示。绝不把 SysUserSession 实体直接返回，
 * 以免将来给实体加字段（尤其 TOKEN_HASH）时不小心泄给前端。</p>
 *
 * <p><b>依赖</b>：无。</p>
 *
 * <p><b>Oracle 注意点</b>：userAgent 为 CLOB，通过 toClob 映射为 String，
 * 接口返回前已按长度截断，避免超长文本撑爆响应体。</p>
 *
 * <p><b>示例</b>：</p>
 * <pre>
 * {"sessionId": "sess_9f2c...", "deviceType": "PC", "deviceInfo": "Chrome 120 / Windows 10",
 *  "ip": "10.1.2.3", "loginTime": "...", "lastActiveTime": "...", "expireTime": "...",
 *  "current": true, "status": "ACTIVE"}
 * </pre>
 */
public class SessionVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 业务会话号。 */
    private String sessionId;

    /** 终端类型。 */
    private String deviceType;

    /** 终端描述。 */
    private String deviceInfo;

    /** 登录 IP。 */
    private String ip;

    /** 登录时间。 */
    private LocalDateTime loginTime;

    /** 最后活跃时间。 */
    private LocalDateTime lastActiveTime;

    /** 过期时间。 */
    private LocalDateTime expireTime;

    /** 逻辑状态。 */
    private String status;

    /** 是否当前正在发起请求的这台终端。 */
    private Boolean current;

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
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

    public LocalDateTime getLoginTime() {
        return loginTime;
    }

    public void setLoginTime(LocalDateTime loginTime) {
        this.loginTime = loginTime;
    }

    public LocalDateTime getLastActiveTime() {
        return lastActiveTime;
    }

    public void setLastActiveTime(LocalDateTime lastActiveTime) {
        this.lastActiveTime = lastActiveTime;
    }

    public LocalDateTime getExpireTime() {
        return expireTime;
    }

    public void setExpireTime(LocalDateTime expireTime) {
        this.expireTime = expireTime;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Boolean getCurrent() {
        return current;
    }

    public void setCurrent(Boolean current) {
        this.current = current;
    }
}
