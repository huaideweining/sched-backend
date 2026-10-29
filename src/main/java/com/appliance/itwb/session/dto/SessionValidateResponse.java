package com.appliance.itwb.session.dto;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 会话校验响应体。
 *
 * <p>接口：GET /api/auth/session/validate、POST /api/auth/session/extend</p>
 *
 * <p><b>文件作用</b>：统一"校验"与"续期"两种动作的返回，前端只需看 valid 与 reason。</p>
 *
 * <p><b>依赖</b>：无。</p>
 *
 * <p><b>Oracle 注意点</b>：时间取自数据库 SYSTIMESTAMP，与 EXPIRE_TIME 比较口径一致。</p>
 *
 * <p><b>示例（有效）</b>：</p>
 * <pre>
 * {"valid": true, "sessionId": "sess_9f2c...", "userId": "u_10001", "deviceType": "PC",
 *  "loginTime": "...", "lastActiveTime": "...", "expireTime": "...", "extended": true, "reason": ""}
 * </pre>
 * <p><b>示例（无效）</b>：{"valid": false, "reason": "会话已过期", "extended": false}</p>
 */
public class SessionValidateResponse implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 是否仍然有效。 */
    private Boolean valid;

    /** 会话号。 */
    private String sessionId;

    /** 用户主键。 */
    private String userId;

    /** 登录名。 */
    private String username;

    /** 终端类型。 */
    private String deviceType;

    /** 登录时间。 */
    private LocalDateTime loginTime;

    /** 最后活跃时间。 */
    private LocalDateTime lastActiveTime;

    /** 过期时间。 */
    private LocalDateTime expireTime;

    /** 本次校验是否触发了一次续期写操作。 */
    private Boolean extended;

    /** 失败原因，成功时为空串。 */
    private String reason = "";

    public Boolean getValid() {
        return valid;
    }

    public void setValid(Boolean valid) {
        this.valid = valid;
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

    public Boolean getExtended() {
        return extended;
    }

    public void setExtended(Boolean extended) {
        this.extended = extended;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }
}
