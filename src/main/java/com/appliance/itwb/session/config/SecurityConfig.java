package com.appliance.itwb.session.config;

import com.appliance.itwb.common.Result;
import com.appliance.itwb.session.filter.TokenAuthenticationFilter;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/**
 * Spring Security 配置：把 {@link TokenAuthenticationFilter} 接入 Security 过滤链。
 *
 * <p><b>文件作用</b>：声明 STATELESS 的 SecurityFilterChain，放行登录等白名单路径，
 * 其余请求一律要求携带有效令牌；401/403 统一输出 {@link Result} JSON，与业务接口同构。</p>
 *
 * <p><b>依赖</b>：spring-boot-starter-security（Security 6.x / Spring Boot 3 命名空间）。</p>
 *
 * <p><b>Oracle 注意点</b>：无。本类不接触数据库。</p>
 *
 * <p><b>设计要点</b>：</p>
 * <ul>
 *   <li><b>为什么用 addFilterBefore 而不是替换过滤器链</b>：令牌校验必须发生在
 *       授权判断（authorizeHttpRequests）之前，授权才能读到过滤器写入的 Authentication；
 *       放在 UsernamePasswordAuthenticationFilter 之前是最小侵入位置，不依赖表单登录存在与否；</li>
 *   <li><b>为什么禁用 Servlet 容器层的自动注册</b>：过滤器标了 @Component，
 *       Spring Boot 会把它同时注册到容器级过滤链，与 Security 链重复执行
 *       （OncePerRequestFilter 虽能保证只跑一次，但语义混乱且顺序不可控）；
 *       用 FilterRegistrationBean.setEnabled(false) 关掉容器级注册，只走 Security 链；</li>
 *   <li><b>STATELESS</b>：不创建/不使用 HTTP 会话，杜绝会话固定攻击；
 *       令牌与 SecurityContext 都不落容器会话，每次请求独立校验；</li>
 *   <li><b>白名单与过滤器放行路径同源</b>：都来自 itwb.session.skip-urls，
 *       改一处两边同时生效，避免"过滤器放行了、Security 又拦住"的隐性不一致；
 *       注意 skip-urls 里不要再包含需要身份的接口（如 /api/auth/logout），
 *       否则过滤器跳过校验，userId 属性为空，Service 层归属校验会直接拒绝；</li>
 *   <li><b>CSRF 关闭的前提</b>：纯 Bearer 令牌 + STATELESS，无 Cookie 会话，
 *       CSRF 攻击面不存在；若将来引入 Cookie（记住我等），必须重新评估；</li>
     *   <li><b>CORS</b>：allowedOriginPatterns 来自 {@code itwb.session.cors-allowed-origins}
     *       （默认含部署态 {@code http://172.16.132.64:8088} + 本地回环/localhost 三类 8088 来源，
     *       Phase C 收紧为具体来源、不再 allow *）；预检请求（OPTIONS）放行，
     *       否则浏览器在携带令牌前就被拦死。</li>
 * </ul>
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private static final Logger log = LoggerFactory.getLogger(SecurityConfig.class);

    private final TokenAuthenticationFilter tokenAuthenticationFilter;
    private final SessionProperties sessionProperties;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public SecurityConfig(TokenAuthenticationFilter tokenAuthenticationFilter,
                          SessionProperties sessionProperties) {
        this.tokenAuthenticationFilter = tokenAuthenticationFilter;
        this.sessionProperties = sessionProperties;
    }

    /**
     * 安全过滤链：白名单放行 + 其余一律认证。
     *
     * <p>令牌校验失败时 {@link TokenAuthenticationFilter} 直接写 401 返回，
     * 不会进入这里的认证入口点；entry point 只作为兜底（例如过滤器被改造后漏写 401 的情况）。</p>
     *
     * @param http HttpSecurity 构建器
     * @return 配置好的过滤链
     * @throws Exception 配置错误时抛出
     */
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        String[] skipUrls = sessionProperties.getSkipUrls();
        http
                // 纯令牌 + 无状态，无 Cookie 会话，CSRF 攻击面不存在
                .csrf(csrf -> csrf.disable())
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                // 不创建/不使用 HTTP 会话：令牌每次请求独立校验，杜绝会话固定攻击
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> {
                    if (skipUrls != null && skipUrls.length > 0) {
                        // 与过滤器共用同一份白名单（Ant 风格模式），保证两边行为一致
                        auth.requestMatchers(skipUrls).permitAll();
                        log.info("[security] 白名单放行路径：{}", Arrays.toString(skipUrls));
                    }
                    auth.anyRequest().authenticated();
                })
                // 401/403 输出与业务接口同构的 Result JSON，前端只认一套契约
                .exceptionHandling(eh -> eh
                        .authenticationEntryPoint(this::writeUnauthorized)
                        .accessDeniedHandler(this::writeForbidden))
                // 令牌校验在授权判断之前执行，授权才能拿到过滤器写入的 Authentication
                .addFilterBefore(tokenAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    /**
     * 禁用过滤器在 Servlet 容器层的自动注册，只保留 Security 链内的注册。
     *
     * <p>否则 @Component 过滤器会同时在容器链和 Security 链出现，
     * OncePerRequestFilter 的 ALREADY_FILTERED 标记会让第二次调用被静默跳过，
     * 行为"看起来正常"但顺序不可控，排查问题时极具迷惑性。</p>
     *
     * @param filter 令牌校验过滤器
     * @return 关闭容器级注册的注册器
     */
    @Bean
    public FilterRegistrationBean<TokenAuthenticationFilter> tokenFilterRegistration(
            TokenAuthenticationFilter filter) {
        FilterRegistrationBean<TokenAuthenticationFilter> registration =
                new FilterRegistrationBean<>(filter);
        registration.setEnabled(false);
        return registration;
    }

    /**
     * CORS 配置（Phase C 收紧，现外部化）：allowedOriginPatterns 来自
     * {@code itwb.session.cors-allowed-origins}（默认含部署态 IP + 本地回环 + localhost 三类 8088 来源），
     * 不再硬编码单一 IP、也不再 allow *。
     *
     * <p>预检请求（OPTIONS）放行，否则浏览器在携带令牌前就被拦死；
     * 允许头含 Authorization / Content-Type / X-Device-Id，支持 Bearer 令牌透传。</p>
     *
     * @return CORS 配置源
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        // Phase C 复盘：此前硬编码 http://172.16.132.64:8088，浏览器以 127.0.0.1 / localhost /
        // 其他 IP 打开 8088 时 Origin 不在白名单，预检 403 且无 ACAO，前端实时数据拉取失败。
        // 现改为读取配置，默认已覆盖三类 8088 来源，生产部署按真实前端地址覆盖即可。
        String[] origins = sessionProperties.getCorsAllowedOrigins();
        config.setAllowedOriginPatterns(Arrays.asList(origins != null ? origins : new String[]{"http://172.16.132.64:8088"}));
        config.setAllowedMethods(Arrays.asList(
                HttpMethod.GET.name(), HttpMethod.POST.name(),
                HttpMethod.PUT.name(), HttpMethod.DELETE.name(),
                HttpMethod.OPTIONS.name()));
        config.setAllowedHeaders(Arrays.asList(
                HttpHeaders.AUTHORIZATION, HttpHeaders.CONTENT_TYPE, "X-Device-Id"));
        config.setMaxAge(3600L);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }

    /**
     * 兜底 401：正常流程下过滤器已提前拦截，此入口点仅在异常场景触发。
     *
     * @param request   当前请求
     * @param response  响应对象
     * @param exception 认证异常
     * @throws IOException 写出失败时抛出
     */
    private void writeUnauthorized(HttpServletRequest request, HttpServletResponse response,
                                   org.springframework.security.core.AuthenticationException exception)
            throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write(objectMapper.writeValueAsString(
                Result.fail(401, "未认证或认证已失效")));
    }

    /**
     * 兜底 403：已认证但权限不足。
     *
     * @param request   当前请求
     * @param response  响应对象
     * @param exception 拒绝访问异常
     * @throws IOException 写出失败时抛出
     */
    private void writeForbidden(HttpServletRequest request, HttpServletResponse response,
                                org.springframework.security.access.AccessDeniedException exception)
            throws IOException {
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write(objectMapper.writeValueAsString(
                Result.fail(403, "权限不足")));
    }
}
