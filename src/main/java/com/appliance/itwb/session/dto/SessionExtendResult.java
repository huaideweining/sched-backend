package com.appliance.itwb.session.dto;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 续期结果（MyBatis 映射用）。
 *
 * <p><b>文件作用</b>：承接 {@code selectExtendResult} 的三列查询，
 * 避免在续期后又拉一次完整实体。</p>
 *
 * <p><b>依赖</b>：无。</p>
 *
 * <p><b>Oracle 注意点</b>：别名与 SQL 中的列别名（status / lastActiveTime / expireTime）
 * 必须逐字一致，否则 Oracle 返回小写列名导致映射失败。/status 列在库中是 VARCHAR2(16)，
 * 这里用 String 接收而不是枚举，避免 MyBatis 枚举处理器在等值查询路径上做多余转换。</p>
 */
public class SessionExtendResult implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 会话状态文本（ACTIVE / EXPIRED / LOGOUT / KICKED）。 */
    private String status;

    /** 最后活跃时间。 */
    private LocalDateTime lastActiveTime;

    /** 过期时间。 */
    private LocalDateTime expireTime;

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
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
}
