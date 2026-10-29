package com.appliance.itwb.session.dto;

import java.io.Serializable;

/**
 * 认证后的用户身份。
 *
 * <p><b>文件作用</b>：把"用户名密码校验"与"会话创建"解耦。会话模块只认这个结果，
 * 不关心密码是走 ERP 接口、LDAP 还是本地表。</p>
 *
 * <p><b>依赖</b>：无。</p>
 *
 * <p><b>Oracle 注意点</b>：userId 建议与 SYS_USER_SESSION.USER_ID 同长度，
 * 默认 64，够放 ERP 的用户 GUID。</p>
 */
public class AuthUser implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 用户主键，会话表的归属字段。 */
    private String userId;

    /** 登录名。 */
    private String username;

    /** 展示用姓名。 */
    private String displayName;

    public AuthUser() {
    }

    /**
     * 构造用户身份。
     *
     * @param userId     用户主键
     * @param username   登录名
     * @param displayName 展示用姓名
     */
    public AuthUser(String userId, String username, String displayName) {
        this.userId = userId;
        this.username = username;
        this.displayName = displayName;
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
}
