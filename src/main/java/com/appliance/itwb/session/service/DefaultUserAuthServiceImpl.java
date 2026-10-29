package com.appliance.itwb.session.service;

import com.appliance.itwb.session.dto.AuthUser;
import org.springframework.stereotype.Service;

/**
 * 用户名密码校验的**占位实现**，与实现方对接前不要上线使用。
 *
 * <p><b>文件作用</b>：让工程可以启动、联调会话以外的功能；真正上线前必须替换。</p>
 *
 * <p><b>依赖</b>：无。</p>
 *
 * <p><b>Oracle 注意点</b>：无。</p>
 *
 * <p><b>请替换成</b>（三选一，贵司 ERP 现状决定）：</p>
 * <ol>
 *   <li>查本地 T_USER 表：{@code select password_hash, status, user_id from t_user where user_code = ?}
 *       ，用与库一致的摘要算法比对，注意 Oracle 的密码列若是 VARCHAR2 存摘要即可，无长度限制问题；</li>
 *   <li>走统一认证/AD/LDAP：直接调用对方接口或 Spring Security 的 DaoAuthenticationProvider；</li>
 *   <li>走其他系统 HTTP 接口：在这个方法里发请求，超时时间务必设置（建议 3 秒），避免拖垮登录。</li>
 * </ol>
 *
 * <p><b>安全要求（重要）</b>：</p>
 * <ul>
 *   <li>密码必须用加盐摘要比对，禁止明文；不要把密码原样打进日志；</li>
 *   <li><b>登录失败计数与锁定必须在这里实现</b>（同一账号连续 N 次失败后锁定 M 分钟），
 *       本会话模块**刻意不重复造一个计数器**——贵司 ERP 大概率已有账号锁定策略，
 *       自建计数器会出现"两套锁定状态不一致"（一边解锁了另一边还锁着）。
 *       推荐做法：读/写贵司用户表的失败计数与锁定时间字段（如 t_user.fail_count / t_user.lock_time），
 *       失败时写审计表 SYS_USER_SESSION_LOG(ACTION_TYPE='LOGIN', RESULT_CODE='FAIL_PASSWORD/FAIL_LOCK')。</li>
 *   <li>锁定时间的计算要以数据库时间（SYSTIMESTAMP）为准，不要用应用服务器时钟。</li>
 * </ul>
 */
@Service
public class DefaultUserAuthServiceImpl implements IUserAuthService {

    /**
     * 默认实现：不做任何校验，直接抛错，防止"忘记替换就上线"。
     *
     * @param username 登录名
     * @param password 密码
     * @return 不会返回（总是抛异常）
     * @throws IllegalStateException 始终抛出，提示必须替换实现
     */
    @Override
    public AuthUser authenticate(String username, String password) {
        throw new IllegalStateException(
                "IUserAuthService 默认实现未接入真实用户体系：请在 "
                        + DefaultUserAuthServiceImpl.class.getName()
                        + " 中按贵司 ERP 的用户表/统一认证接口实现 authenticate()");
    }
}
