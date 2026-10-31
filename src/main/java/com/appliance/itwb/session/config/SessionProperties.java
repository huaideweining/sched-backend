package com.appliance.itwb.session.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * 会话参数配置类。
 *
 * <p><b>文件作用</b>：把 application.yml 中 {@code itwb.session.*} 的取值集中到 Java，
 * 业务代码只读配置、不读字符串常量，改参数不用动代码。</p>
 *
 * <p><b>依赖</b>：spring-boot（@ConfigurationProperties）。</p>
 *
 * <p><b>Oracle 注意点</b>：Duration 会被 Spring 转成毫秒传给 Mapper，
 * Service 再除以 1000 得到秒；Oracle 的日期算术以"天"为单位，所以只有整秒值能精确落到 TIMESTAMP(6)。</p>
 *
 * <p><b>参数说明（yml 写法）</b>：</p>
 * <pre>
 * itwb:
 *   session:
 *     expire-duration:        8h    # 绝对生命周期上限，从登录时刻起算，防无限续期
 *     max-idle-duration:      30m   # 最大空闲时长，滑动续期按它前移过期时间
 *     extend-threshold:       5m    # 剩余有效期低于它才写库续期，控制写放大
 *     multi-login-enabled:    true  # true=多端并存；false=单点登录（同设备踢旧）
 *     max-sessions-per-user:  5     # 单用户最大会话数，0 表示不限制
 *     device-binding-enabled: false # true=校验终端指纹，令牌不能跨设备使用
 *     remember-me-duration:   30d   # 记住我的过期时长
 *     cleanup-cron:            "0 0/10 * * * ?"
 *     cleanup-enabled:         true
 * </pre>
 */
@Component
@ConfigurationProperties(prefix = "itwb.session")
public class SessionProperties {

    /** 绝对生命周期上限（从 LOGIN_TIME 起算），滑动续期不得越过它。 */
    private Duration expireDuration = Duration.ofHours(8);

    /** 最大空闲时长：连续无操作超过该时长即失效。 */
    private Duration maxIdleDuration = Duration.ofMinutes(30);

    /** 续期阈值：剩余有效期低于该值时，校验/续期动作才真正写库。 */
    private Duration extendThreshold = Duration.ofMinutes(5);

    /** 是否允许多端并存，false 时按同设备类型踢掉旧会话。 */
    private Boolean multiLoginEnabled = Boolean.TRUE;

    /** 单用户最大会话数，0 表示不限制；超限时淘汰最旧的会话。 */
    private Integer maxSessionsPerUser = 5;

    /** 是否开启终端指纹绑定校验。 */
    private Boolean deviceBindingEnabled = Boolean.FALSE;

    /** 记住我的过期时长。 */
    private Duration rememberMeDuration = Duration.ofDays(30);

    /** 过期会话清理任务的 cron 表达式。 */
    private String cleanupCron = "0 0/10 * * * ?";

    /** 是否启用清理任务。 */
    private Boolean cleanupEnabled = Boolean.TRUE;

    /** 单次清理的最大行数。 */
    private int cleanupBatchSize = 1000;

    /** 令牌校验过滤器放行的路径，逗号分隔。与 Security 白名单同源，改一处两边生效。
     *  <p>Phase C 收紧：移除 /swagger-ui、/v3/api-docs（项目未引入 Swagger 依赖，属历史残留），
     *  仅保留真正需要匿名放行的三类路径。任何携带身份的接口都不得加入此名单。</p> */
    private String[] skipUrls = new String[]{
            "/api/auth/login",
            "/api/auth/register",
            "/favicon.ico",
            "/actuator/health"
    };

    /**
     * CORS 允许的前端来源（Origin 模式，逗号分隔；支持 http(s)://host:port 或 *）。
     *
     * <p><b>Phase C 复盘</b>：此前硬编码为单一部署 IP {@code http://172.16.132.64:8088}。
     * 当浏览器以 127.0.0.1 / localhost / 其他 IP 打开 8088 静态服务器时，请求头里的 Origin
     * 不在白名单，预检（OPTIONS）直接 403 且不返回 Access-Control-Allow-Origin，
     * 前端带令牌的实时数据请求被浏览器拦截、无法从 18077 拉取。</p>
     *
     * <p>现外部化为配置，默认覆盖「部署态 IP + 本地回环 + localhost」三类 8088 来源；
     * 生产部署按真实前端地址覆盖即可。长期不要 allow *：带 Bearer 令牌透传时放 '*' 等于裸奔。</p>
     */
    private String[] corsAllowedOrigins = new String[]{
            "http://172.16.132.64:8088",
            "http://127.0.0.1:8088",
            "http://localhost:8088"
    };

    public Duration getExpireDuration() {
        return expireDuration;
    }

    public void setExpireDuration(Duration expireDuration) {
        this.expireDuration = expireDuration;
    }

    public Duration getMaxIdleDuration() {
        return maxIdleDuration;
    }

    public void setMaxIdleDuration(Duration maxIdleDuration) {
        this.maxIdleDuration = maxIdleDuration;
    }

    public Duration getExtendThreshold() {
        return extendThreshold;
    }

    public void setExtendThreshold(Duration extendThreshold) {
        this.extendThreshold = extendThreshold;
    }

    public Boolean getMultiLoginEnabled() {
        return multiLoginEnabled;
    }

    public void setMultiLoginEnabled(Boolean multiLoginEnabled) {
        this.multiLoginEnabled = multiLoginEnabled;
    }

    public Integer getMaxSessionsPerUser() {
        return maxSessionsPerUser;
    }

    public void setMaxSessionsPerUser(Integer maxSessionsPerUser) {
        this.maxSessionsPerUser = maxSessionsPerUser;
    }

    public Boolean getDeviceBindingEnabled() {
        return deviceBindingEnabled;
    }

    public void setDeviceBindingEnabled(Boolean deviceBindingEnabled) {
        this.deviceBindingEnabled = deviceBindingEnabled;
    }

    public Duration getRememberMeDuration() {
        return rememberMeDuration;
    }

    public void setRememberMeDuration(Duration rememberMeDuration) {
        this.rememberMeDuration = rememberMeDuration;
    }

    public String getCleanupCron() {
        return cleanupCron;
    }

    public void setCleanupCron(String cleanupCron) {
        this.cleanupCron = cleanupCron;
    }

    public Boolean getCleanupEnabled() {
        return cleanupEnabled;
    }

    public void setCleanupEnabled(Boolean cleanupEnabled) {
        this.cleanupEnabled = cleanupEnabled;
    }

    public int getCleanupBatchSize() {
        return cleanupBatchSize;
    }

    public void setCleanupBatchSize(int cleanupBatchSize) {
        this.cleanupBatchSize = cleanupBatchSize;
    }

    public String[] getSkipUrls() {
        return skipUrls;
    }

    public void setSkipUrls(String[] skipUrls) {
        this.skipUrls = skipUrls;
    }

    public String[] getCorsAllowedOrigins() {
        return corsAllowedOrigins;
    }

    public void setCorsAllowedOrigins(String[] corsAllowedOrigins) {
        this.corsAllowedOrigins = corsAllowedOrigins;
    }
}
