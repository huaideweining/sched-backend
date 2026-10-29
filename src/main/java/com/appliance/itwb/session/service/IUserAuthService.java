package com.appliance.itwb.session.service;

import com.appliance.itwb.session.dto.AuthUser;

/**
 * 用户名/密码校验服务（**需要按企业实际情况对接**）。
 *
 * <p><b>文件作用</b>：把"认证"与"会话"解耦。会话模块只依赖这个返回值，
 * 不关心密码是存在 ERP 表里、走 LDAP/AD，还是调用统一认证中心。</p>
 *
 * <p><b>依赖</b>：无。</p>
 *
 * <p><b>Oracle 注意点</b>：本接口不碰数据库。若校验逻辑本身要查用户表，
 * 请复用项目里已有的 UserMapper，不要在这里另建表。</p>
 *
 * <p><b>必须替换的默认实现</b>：见 {@link DefaultUserAuthServiceImpl}。
 * 默认实现会直接抛异常，因为它不知道贵司的用户表结构与密码策略——
 * 这是本工程唯一一处"必须由你来实现"的地方。</p>
 */
public interface IUserAuthService {

    /**
     * 校验用户名与密码。
     *
     * @param username 登录名
     * @param password 密码明文
     * @return 认证通过后的用户身份
     * @throws RuntimeException 认证失败或用户不存在时抛异常，调用方统一转成 401
     */
    AuthUser authenticate(String username, String password);
}
