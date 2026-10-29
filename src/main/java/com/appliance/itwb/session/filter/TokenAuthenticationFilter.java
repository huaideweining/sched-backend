package com.appliance.itwb.session.filter;

import com.appliance.itwb.common.Result;
import com.appliance.itwb.session.config.SessionProperties;
import com.appliance.itwb.session.dto.SessionValidateResponse;
import com.appliance.itwb.session.service.ISysUserSessionService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
// 注意：Spring Framework 6 起 AntPathMatcher 位于 org.springframework.util，
// 不在 org.springframework.web.util（后者承载的是 PathPatternParser 相关的工具类）
import org.springframework.util.AntPathMatcher;
import org.springframework.util.PathMatcher;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 令牌校验过滤器（已接入 Spring Security，由 SecurityConfig 注册到过滤链）。
 *
 * <p><b>文件作用</b>：在每个请求进入授权判断之前校验 Bearer 令牌，
 * 通过后同时做两件事：请求属性（供 Controller 直读）+ SecurityContext
 * （供授权规则与 @AuthenticationPrincipal 等使用），失败则直接返回 401。</p>
 *
 * <p><b>依赖</b>：jakarta.servlet、Spring Web、Spring Security core、{@link ISysUserSessionService}。
 * 由 {@code SecurityConfig} 通过 addFilterBefore 注册，容器级注册已被禁用（见 FilterRegistrationBean）。</p>
 *
 * <p><b>Oracle 注意点</b>：无。过滤器与数据库方言无关。</p>
 *
 * <p><b>要点说明</b>：</p>
 * <ul>
 *   <li>过期判定交给 Service 的原子 SQL，过滤器只负责"有没有效"和"把身份传给下游"；</li>
 *   <li>{@code slide} 传 false：过滤器属于高频路径，不产生写操作，续期交给业务接口；</li>
 *   <li>401 统一输出 {@link Result} 结构，前端按 {@code code} 处理；
 *       用 {@code ObjectMapper} 写回 JSON，避免引入额外的 HttpMessageConverter 依赖；</li>
 *   <li><b>SecurityContext 生命周期</b>：认证对象只挂在当前线程的 ThreadLocal 上，
 *       STATELESS 模式没有 HTTP 会话兜底，链路结束必须在 finally 里 clearContext()，
 *       否则容器线程复用会把上一个请求的身份"串"给下一个请求——这是无状态过滤器最隐蔽的事故源；</li>
 *   <li><b>authorities 固定为 ROLE_USER</b>：会话表不含角色信息，无法在过滤器内扩权。
 *       若需按角色鉴权（hasRole('ADMIN')），在此处按 userId 查角色后填充 authorities，
 *       并保持"宁可少给不能多给"的原则；</li>
 *   <li>会话属性名（ITWB_USER_ID / ITWB_SESSION_ID / ITWB_DEVICE_ID）由下游 Controller 通过
 *       {@code request.getAttribute(...)} 读取，与 SecurityContext 并存互不影响，
 *       老代码零改动。</li>
 * </ul>
 *
 * <p><b>注意</b>：默认放行路径来自 {@code itwb.session.skip-urls}，与 Security 白名单同源。
 * 白名单里不要再包含需要身份的接口（如 /api/auth/logout），否则本过滤器跳过校验、
 * userId 属性为空，Service 层归属校验会直接拒绝该请求。</p>
 */
@Component
public class TokenAuthenticationFilter extends OncePerRequestFilter {

    /** 请求属性：用户主键。 */
    public static final String ATTR_USER_ID = "ITWB_USER_ID";

    /** 请求属性：业务会话号。 */
    public static final String ATTR_SESSION_ID = "ITWB_SESSION_ID";

    /** 请求属性：终端指纹。 */
    public static final String ATTR_DEVICE_ID = "ITWB_DEVICE_ID";

    private static final Logger log = LoggerFactory.getLogger(TokenAuthenticationFilter.class);

    private static final String BEARER_PREFIX = "Bearer ";

    private static final int STATUS_UNAUTHORIZED = 401;

    /**
     * 默认权限：会话表不含角色信息，统一授予 ROLE_USER。
     * 需要按角色鉴权时，在此处按 userId 查角色后替换。
     */
    private static final List<SimpleGrantedAuthority> DEFAULT_AUTHORITIES =
            Collections.singletonList(new SimpleGrantedAuthority("ROLE_USER"));

    private final ISysUserSessionService sessionService;
    private final SessionProperties sessionProperties;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final PathMatcher pathMatcher = new AntPathMatcher();

    /**
     * 构造过滤器。
     *
     * @param sessionService   会话服务
     * @param sessionProperties 会话配置（放行路径来自这里）
     */
    public TokenAuthenticationFilter(ISysUserSessionService sessionService,
                                     SessionProperties sessionProperties) {
        this.sessionService = sessionService;
        this.sessionProperties = sessionProperties;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String requestUri = request.getRequestURI();
        if (isSkip(requestUri)) {
            filterChain.doFilter(request, response);
            return;
        }

        String token = resolveToken(request);
        if (!StringUtils.hasText(token)) {
            writeUnauthorized(response, "缺少访问令牌");
            return;
        }

        // slide=false：过滤器高频执行，不做任何写库动作
        SessionValidateResponse validateResult = sessionService.validate(token, request.getHeader("X-Device-Id"), false);
        if (!Boolean.TRUE.equals(validateResult.getValid())) {
            log.info("[filter] 令牌校验失败 uri={}, sessionId={}, reason={}",
                    requestUri, validateResult.getSessionId(), validateResult.getReason());
            writeUnauthorized(response, validateResult.getReason());
            return;
        }

        request.setAttribute(ATTR_USER_ID, validateResult.getUserId());
        request.setAttribute(ATTR_SESSION_ID, validateResult.getSessionId());
        String deviceId = request.getHeader("X-Device-Id");
        request.setAttribute(ATTR_DEVICE_ID, deviceId);

        // 写入 SecurityContext：授权规则（authenticated/hasRole）与 @AuthenticationPrincipal 都从这里取
        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(validateResult.getUserId(), null, DEFAULT_AUTHORITIES);
        Map<String, String> details = new HashMap<>(4);
        details.put("sessionId", validateResult.getSessionId());
        details.put("deviceId", deviceId == null ? "" : deviceId);
        authentication.setDetails(details);
        SecurityContextHolder.getContext().setAuthentication(authentication);

        try {
            filterChain.doFilter(request, response);
        } finally {
            // STATELESS 无 HTTP 会话兜底，必须清 ThreadLocal，防止容器线程复用时身份串号
            SecurityContextHolder.clearContext();
        }
    }

    /**
     * 从请求头解析 Bearer 令牌，兼容大小写与多余空格。
     *
     * @param request 当前请求
     * @return 令牌原文，未携带返回空串
     */
    private String resolveToken(HttpServletRequest request) {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (!StringUtils.hasText(header)) {
            return "";
        }
        if (header.length() > BEARER_PREFIX.length()
                && header.regionMatches(true, 0, BEARER_PREFIX, 0, BEARER_PREFIX.length())) {
            return header.substring(BEARER_PREFIX.length()).trim();
        }
        // 部分老系统直接把令牌放在 Authorization 头里，兼容这种不带 Bearer 前缀的写法
        return header.trim();
    }

    /**
     * 判断当前路径是否放行。
     *
     * @param requestUri 请求路径
     * @return 放行返回 true
     */
    private boolean isSkip(String requestUri) {
        String[] skipUrls = sessionProperties.getSkipUrls();
        if (skipUrls == null) {
            return false;
        }
        for (String skipUrl : skipUrls) {
            if (!StringUtils.hasText(skipUrl)) {
                continue;
            }
            if (pathMatcher.match(skipUrl, requestUri)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 输出 401 JSON。
     *
     * @param response 响应对象
     * @param message  提示信息
     * @throws IOException 写出失败时抛出
     */
    private void writeUnauthorized(HttpServletResponse response, String message) throws IOException {
        response.setStatus(STATUS_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        Result<String> body = Result.fail(401, message);
        response.getWriter().write(objectMapper.writeValueAsString(body));
    }
}
