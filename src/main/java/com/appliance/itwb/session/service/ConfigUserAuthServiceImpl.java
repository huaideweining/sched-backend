package com.appliance.itwb.session.service;

import com.appliance.itwb.common.AuthException;
import com.appliance.itwb.session.dto.AuthUser;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

/**
 * 基于配置文件的固定账号认证实现（内网单人工具的过渡方案）。
 *
 * <p><b>文件作用</b>：替代 {@link DefaultUserAuthServiceImpl} 的抛错占位实现，
 * 让登录联调可以走通。账号/密码/USER_ID 全部来自 application.yml 的 {@code itwb.auth.*}，
 * 改密码只需改配置并重启，不动代码。</p>
 *
 * <p><b>依赖</b>：spring-beans（@Value）。</p>
 *
 * <p><b>Oracle 注意点</b>：user-id 必须与业务表（WB_ITEM/WB_PROJECT/WB_NOTE）已有数据的
 * USER_ID 一致，否则登录后快照读出来是空的。</p>
 *
 * <p><b>后续替换</b>：接 ERP T_USER / LDAP 时，按 {@link DefaultUserAuthServiceImpl}
 * 类注释里的三个方案实现一个新的 IUserAuthService 并加 @Primary 即可，本类直接删除。</p>
 */
@Service
@Primary
public class ConfigUserAuthServiceImpl implements IUserAuthService {

    /** 登录名（前端登录框输入的用户名）。 */
    @Value("${itwb.auth.username}")
    private String configUsername;

    /** 密码（明文存配置，仅限内网单人工具；接 ERP 后此配置作废）。 */
    @Value("${itwb.auth.password}")
    private String configPassword;

    /** 用户主键：会话表与业务表按它隔离数据，必须与已导入数据的 USER_ID 一致。 */
    @Value("${itwb.auth.user-id}")
    private String configUserId;

    /** 展示名，缺省用登录名。 */
    @Value("${itwb.auth.display-name:}")
    private String configDisplayName;

    @Override
    public AuthUser authenticate(String username, String password) {
        if (configUsername.equals(username) && configPassword.equals(password)) {
            return new AuthUser(configUserId, username,
                    (configDisplayName == null || configDisplayName.isEmpty()) ? username : configDisplayName);
        }
        // 不写日志明文密码；不区分"用户不存在/密码错"，避免账号探测
        throw new AuthException("用户名或密码错误");
    }
}
