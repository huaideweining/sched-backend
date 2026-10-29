package com.appliance.itwb.session.dto;

import java.io.Serializable;

/**
 * 主动续期请求体。
 *
 * <p>接口：POST /api/auth/session/extend</p>
 *
 * <p><b>文件作用</b>：前端在"剩余有效期低于阈值"或"用户主动点击延长登录"时调用。</p>
 *
 * <p><b>依赖</b>：无。</p>
 *
 * <p><b>Oracle 注意点</b>：续期在 SQL 内用 LEAST 与"总生命周期上限"取小，
 * 不会因为高频调用而把会话无限延长。</p>
 *
 * <p><b>示例</b>：{"sessionId": "sess_9f2c..."}</p>
 */
public class ExtendRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 会话号；未传时取当前请求头令牌对应的会话。 */
    private String sessionId;

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }
}
