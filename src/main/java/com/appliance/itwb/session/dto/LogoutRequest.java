package com.appliance.itwb.session.dto;

import java.io.Serializable;

/**
 * 登出请求体。
 *
 * <p>接口：POST /api/auth/logout</p>
 *
 * <p><b>文件作用</b>：支持"登出当前设备"和"登出全部设备"两种语义。</p>
 *
 * <p><b>依赖</b>：无。</p>
 *
 * <p><b>Oracle 注意点</b>：无。</p>
 *
 * <p><b>示例</b>：{"logoutAll": true, "sessionId": "sess_9f2c..."}</p>
 */
public class LogoutRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /** true=登出该用户全部终端；false=仅登出本次会话。 */
    private Boolean logoutAll = Boolean.FALSE;

    /** 会话号；未传时使用请求头 Authorization 中的令牌对应的会话。 */
    private String sessionId;

    public Boolean getLogoutAll() {
        return logoutAll;
    }

    public void setLogoutAll(Boolean logoutAll) {
        this.logoutAll = logoutAll;
    }

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }
}
