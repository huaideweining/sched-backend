package com.appliance.itwb.session.dto;

import java.io.Serializable;

/**
 * 登录请求体。
 *
 * <p>接口：POST /api/auth/login</p>
 *
 * <p><b>文件作用</b>：接收登录参数，字段名与前端表单一一对应。</p>
 *
 * <p><b>依赖</b>：无（Jackson 反序列化）。</p>
 *
 * <p><b>Oracle 注意点</b>：无。</p>
 *
 * <p><b>示例</b>：</p>
 * <pre>
 * {
 *   "username": "zhangsan",
 *   "password": "...",
 *   "deviceType": "PC",
 *   "deviceId": "PC-20240513-XXXX",
 *   "deviceInfo": "Chrome 120 / Windows 10",
 *   "rememberMe": false
 * }
 * </pre>
 */
public class LoginRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 登录名。 */
    private String username;

    /** 密码（明文，仅在 HTTPS 下传输；服务端校验后立即丢弃，不落库）。 */
    private String password;

    /** 终端类型：PC / MOBILE / TABLET / MINI_PROGRAM / OTHER，为空时默认 PC。 */
    private String deviceType;

    /** 终端指纹，建议前端生成并持久化（localStorage），用于识别"同一台设备"。 */
    private String deviceId;

    /** 终端描述，展示在在线终端列表。 */
    private String deviceInfo;

    /** 记住我：过期时长按 remember-me-expire-duration 计算。 */
    private Boolean rememberMe = Boolean.FALSE;

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getDeviceType() {
        return deviceType;
    }

    public void setDeviceType(String deviceType) {
        this.deviceType = deviceType;
    }

    public String getDeviceId() {
        return deviceId;
    }

    public void setDeviceId(String deviceId) {
        this.deviceId = deviceId;
    }

    public String getDeviceInfo() {
        return deviceInfo;
    }

    public void setDeviceInfo(String deviceInfo) {
        this.deviceInfo = deviceInfo;
    }

    public Boolean getRememberMe() {
        return rememberMe;
    }

    public void setRememberMe(Boolean rememberMe) {
        this.rememberMe = rememberMe;
    }
}
