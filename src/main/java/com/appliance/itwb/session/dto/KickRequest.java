package com.appliance.itwb.session.dto;

import java.io.Serializable;

/**
 * 踢下线请求体。
 *
 * <p>接口：POST /api/auth/sessions/kick</p>
 *
 * <p><b>文件作用</b>：统一三种踢人范围，前端勾选项与之一一对应。</p>
 *
 * <p><b>依赖</b>：无。</p>
 *
 * <p><b>Oracle 注意点</b>：scope=SAME_DEVICE 时按 DEVICE_TYPE 精确踢，
 * 不依赖设备指纹，避免设备指纹丢失（换浏览器、清缓存）后踢不掉。</p>
 *
 * <p><b>示例</b>：</p>
 * <pre>
 * {"scope": "CURRENT_USER", "sessionId": "sess_9f2c...", "remark": "用户手动踢下线"}
 * {"scope": "SAME_DEVICE", "deviceType": "PC", "remark": "同设备顶替"}
 * </pre>
 */
public class KickRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 踢人范围：
     * <ul>
     *   <li>CURRENT_USER：踢掉当前登录用户的指定会话；</li>
     *   <li>SAME_DEVICE：踢掉当前登录用户同类型的全部终端；</li>
     *   <li>ALL_DEVICES：踢掉当前登录用户的全部终端。</li>
     * </ul>
     */
    private String scope;

    /** 会话号，scope=CURRENT_USER 时必填。 */
    private String sessionId;

    /** 终端类型，scope=SAME_DEVICE 时必填。 */
    private String deviceType;

    /** 踢出原因，展示在在线终端列表与审计日志。 */
    private String remark;

    public String getScope() {
        return scope;
    }

    public void setScope(String scope) {
        this.scope = scope;
    }

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

    public String getRemark() {
        return remark;
    }

    public void setRemark(String remark) {
        this.remark = remark;
    }
}
