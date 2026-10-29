package com.appliance.itwb.session.controller;

import com.appliance.itwb.common.Result;
import com.appliance.itwb.session.dto.ExtendRequest;
import com.appliance.itwb.session.dto.KickRequest;
import com.appliance.itwb.session.dto.LoginRequest;
import com.appliance.itwb.session.dto.LoginResponse;
import com.appliance.itwb.session.dto.LogoutRequest;
import com.appliance.itwb.session.dto.SessionPageQuery;
import com.appliance.itwb.session.dto.SessionPageResult;
import com.appliance.itwb.session.dto.SessionValidateRequest;
import com.appliance.itwb.session.dto.SessionValidateResponse;
import com.appliance.itwb.session.dto.SessionVO;
import com.appliance.itwb.session.filter.TokenAuthenticationFilter;
import com.appliance.itwb.session.service.ISysUserSessionService;
import com.workbuddy.sched.auth.RegisterRequest;
import com.workbuddy.sched.dto.RegisterResp;
import com.workbuddy.sched.service.RegisterService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 认证与会话管理接口。
 *
 * <p><b>文件作用</b>：对外暴露登录、登出、会话校验、续期、在线终端查询、踢下线六个接口。</p>
 *
 * <p><b>依赖</b>：spring-web。所有响应统一包成 {@link Result}，绝不直接返回实体。</p>
 *
 * <p><b>Oracle 注意点</b>：无。控制器不感知数据库方言。</p>
 *
 * <p><b>接口清单</b>：</p>
 * <table border="1" summary="接口清单">
 *   <tr><th>方法</th><th>路径</th><th>请求体/参数</th><th>响应 data</th></tr>
 *   <tr>
 *     <td>POST</td><td>/api/auth/login</td>
 *     <td>username, password, deviceType, deviceId, deviceInfo, rememberMe</td>
 *     <td>LoginResponse</td>
 *   </tr>
 *   <tr>
 *     <td>POST</td><td>/api/auth/logout</td>
 *     <td>logoutAll(boolean), sessionId(可选)</td>
 *     <td>无（null）</td>
 *   </tr>
 *   <tr>
 *     <td>GET</td><td>/api/auth/session/validate</td>
 *     <td>查询串 deviceId, slide(默认 true)</td>
 *     <td>SessionValidateResponse</td>
 *   </tr>
 *   <tr>
 *     <td>POST</td><td>/api/auth/session/extend</td>
 *     <td>sessionId(可选)</td>
 *     <td>SessionValidateResponse</td>
 *   </tr>
 *   <tr>
 *     <td>GET</td><td>/api/auth/sessions</td>
 *     <td>查询串 pageNum, pageSize, deviceType, keyword</td>
 *     <td>SessionPageResult&lt;SessionVO&gt;</td>
 *   </tr>
 *   <tr>
 *     <td>POST</td><td>/api/auth/sessions/kick</td>
 *     <td>scope, sessionId, deviceType, remark</td>
 *     <td>Integer（被踢掉的会话数）</td>
 *   </tr>
 * </table>
 *
 * <p><b>统一请求头</b>：</p>
 * <ul>
 *   <li>{@code Authorization: Bearer {token}} —— 除登录外的所有接口必带；</li>
 *   <li>{@code X-Device-Id: {设备指纹}} —— 可选，开启设备绑定校验时建议带上。</li>
 * </ul>
 *
 * <p><b>统一返回示例</b>：</p>
 * <pre>
 * {"code": 0, "success": true, "msg": "success", "data": {...}, "timestamp": 1716000000000}
 * </pre>
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private static final Logger log = LoggerFactory.getLogger(AuthController.class);

    /** 踢人范围：踢单个会话。 */
    private static final String SCOPE_CURRENT_USER = "CURRENT_USER";

    /** 踢人范围：踢掉同类型终端。 */
    private static final String SCOPE_SAME_DEVICE = "SAME_DEVICE";

    /** 踢人范围：踢掉全部终端。 */
    private static final String SCOPE_ALL_DEVICES = "ALL_DEVICES";

    private final ISysUserSessionService sessionService;
    private final RegisterService registerService;

    /**
     * 构造控制器。
     *
     * @param sessionService 会话服务
     */
    public AuthController(ISysUserSessionService sessionService, RegisterService registerService) {
        this.sessionService = sessionService;
        this.registerService = registerService;
    }

    /**
     * 登录。
     *
     * <p>客户端 IP 由服务端从 X-Forwarded-For / X-Real-IP / 远端地址解析，不信任前端传参。</p>
     *
     * @param request  登录请求
     * @param httpRequest 原始请求（用于取 IP 与 UA）
     * @return 令牌与会话信息
     */
    @PostMapping("/login")
    public Result<LoginResponse> login(@RequestBody LoginRequest request,
                                       HttpServletRequest httpRequest) {
        if (request == null || !StringUtils.hasText(request.getUsername())
                || !StringUtils.hasText(request.getPassword())) {
            return Result.fail(400, "用户名或密码不能为空");
        }
        String clientIp = resolveClientIp(httpRequest);
        String userAgent = httpRequest.getHeader(HttpHeaders.USER_AGENT);
        LoginResponse response = sessionService.login(request, clientIp, userAgent);
        return Result.ok(response);
    }

    /**
     * 调度用户注册（R4）。匿名开放，仅当 {@code /api/auth/register} 在
     * {@code itwb.session.skip-urls} 白名单时才可访问；响应统一包成 {@link Result}。
     *
     * <p>原 {@code SchedAuthController}（com.workbuddy.sched）已合并至此，以消除
     * {@code /api/auth} 命名空间下的双 bean 漂移；注册逻辑仍委托 {@link RegisterService}。</p>
     *
     * @param req 注册请求（username 3~32、password 6~128）
     * @return 注册结果
     */
    @PostMapping("/register")
    public Result<RegisterResp> register(@Valid @RequestBody RegisterRequest req) {
        return Result.ok(registerService.register(req));
    }

    /**
     * 登出（当前设备 / 全部设备）。
     *
     * @param request     登出请求
     * @param httpRequest 原始请求（令牌来自这里）
     * @return 统一返回
     */
    @PostMapping("/logout")
    public Result<Integer> logout(@RequestBody(required = false) LogoutRequest request,
                                  HttpServletRequest httpRequest) {
        String token = resolveToken(httpRequest);
        String sessionId = currentSessionId(httpRequest);
        if (request != null && StringUtils.hasText(request.getSessionId())) {
            // 允许显式传自己的会话号；Service 层仍会校验归属，传别人的会被拒绝
            sessionId = request.getSessionId();
        }
        boolean logoutAll = request != null && Boolean.TRUE.equals(request.getLogoutAll());
        int rows = sessionService.logout(token, sessionId, logoutAll, currentUserId(httpRequest));
        return Result.ok(rows);
    }

    /**
     * 校验会话（可配置为只校验不续期）。
     *
     * @param request     校验参数
     * @param httpRequest 原始请求（令牌来自这里）
     * @return 校验结果
     */
    @GetMapping("/session/validate")
    public Result<SessionValidateResponse> validate(SessionValidateRequest request,
                                                    HttpServletRequest httpRequest) {
        if (request == null) {
            request = new SessionValidateRequest();
        }
        String token = resolveToken(httpRequest);
        String deviceId = request.getDeviceId();
        if (!StringUtils.hasText(deviceId)) {
            deviceId = httpRequest.getHeader("X-Device-Id");
        }
        boolean slide = !Boolean.FALSE.equals(request.getSlide());
        SessionValidateResponse response = sessionService.validate(token, deviceId, slide);
        return Result.ok(response);
    }

    /**
     * 主动续期。
     *
     * @param request     续期请求
     * @param httpRequest 原始请求（令牌来自这里）
     * @return 续期结果
     */
    @PostMapping("/session/extend")
    public Result<SessionValidateResponse> extend(@RequestBody(required = false) ExtendRequest request,
                                                  HttpServletRequest httpRequest) {
        String sessionId = null;
        if (request != null) {
            sessionId = request.getSessionId();
        }
        if (!StringUtils.hasText(sessionId)) {
            sessionId = currentSessionId(httpRequest);
        }
        SessionValidateResponse response = sessionService.extend(resolveToken(httpRequest), sessionId);
        return Result.ok(response);
    }

    /**
     * 查询当前账号的在线终端（分页）。
     *
     * <p>userId 来自令牌解析结果，不接受前端传参，避免越权查别人的会话。</p>
     *
     * @param query       分页与筛选条件
     * @param httpRequest 原始请求
     * @return 分页结果
     */
    @GetMapping("/sessions")
    public Result<SessionPageResult<SessionVO>> listSessions(SessionPageQuery query,
                                                             HttpServletRequest httpRequest) {
        if (query == null) {
            query = new SessionPageQuery();
        }
        String userId = currentUserId(httpRequest);
        SessionPageResult<SessionVO> result =
                sessionService.listUserSessions(userId, query, currentSessionId(httpRequest));
        return Result.ok(result);
    }

    /**
     * 踢下线：支持踢单个会话、踢同类型终端、踢全部终端。
     *
     * @param request     踢人请求
     * @param httpRequest 原始请求
     * @return 被踢掉的会话数
     */
    @PostMapping("/sessions/kick")
    public Result<Integer> kick(@RequestBody(required = false) KickRequest request,
                                HttpServletRequest httpRequest) {
        if (request == null || !StringUtils.hasText(request.getScope())) {
            return Result.fail(400, "scope 不能为空");
        }
        String operatorId = currentUserId(httpRequest);
        String remark = StringUtils.hasText(request.getRemark()) ? request.getRemark() : "用户手动踢下线";

        int rows;
        if (SCOPE_CURRENT_USER.equals(request.getScope())) {
            if (!StringUtils.hasText(request.getSessionId())) {
                return Result.fail(400, "sessionId 不能为空");
            }
            boolean success = sessionService.kick(request.getSessionId(), operatorId, remark);
            rows = success ? 1 : 0;
        } else if (SCOPE_SAME_DEVICE.equals(request.getScope())) {
            if (!StringUtils.hasText(request.getDeviceType())) {
                return Result.fail(400, "deviceType 不能为空");
            }
            rows = sessionService.kickByDeviceType(operatorId, request.getDeviceType(), operatorId, remark);
        } else if (SCOPE_ALL_DEVICES.equals(request.getScope())) {
            rows = sessionService.kickAllByUserId(operatorId, operatorId, remark);
        } else {
            return Result.fail(400, "scope 取值非法，允许值：CURRENT_USER / SAME_DEVICE / ALL_DEVICES");
        }
        log.info("[session] 踢下线 operator={}, scope={}, rows={}", operatorId, request.getScope(), rows);
        return Result.ok(rows);
    }

    /**
     * 从请求头解析令牌。
     *
     * @param httpRequest 原始请求
     * @return 令牌原文
     */
    private String resolveToken(HttpServletRequest httpRequest) {
        String header = httpRequest.getHeader(HttpHeaders.AUTHORIZATION);
        if (!StringUtils.hasText(header)) {
            return "";
        }
        if (header.length() > 7 && header.regionMatches(true, 0, "Bearer ", 0, 7)) {
            return header.substring(7).trim();
        }
        return header.trim();
    }

    /**
     * 取过滤器解析出的用户主键。
     *
     * @param httpRequest 原始请求
     * @return 用户主键
     */
    private String currentUserId(HttpServletRequest httpRequest) {
        Object userId = httpRequest.getAttribute(TokenAuthenticationFilter.ATTR_USER_ID);
        return userId == null ? "" : userId.toString();
    }

    /**
     * 取过滤器解析出的会话号。
     *
     * @param httpRequest 原始请求
     * @return 会话号
     */
    private String currentSessionId(HttpServletRequest httpRequest) {
        Object sessionId = httpRequest.getAttribute(TokenAuthenticationFilter.ATTR_SESSION_ID);
        return sessionId == null ? "" : sessionId.toString();
    }

    /**
     * 解析客户端真实 IP，兼容反向代理场景。
     *
     * @param httpRequest 原始请求
     * @return IP 地址，取不到返回 0.0.0.0
     */
    private String resolveClientIp(HttpServletRequest httpRequest) {
        String ip = httpRequest.getHeader("X-Forwarded-For");
        if (StringUtils.hasText(ip)) {
            // 多级代理时格式为 "client, proxy1, proxy2"，取第一个
            int commaIndex = ip.indexOf(',');
            if (commaIndex > 0) {
                ip = ip.substring(0, commaIndex);
            }
        } else {
            ip = httpRequest.getHeader("X-Real-IP");
        }
        if (!StringUtils.hasText(ip)) {
            ip = httpRequest.getRemoteAddr();
        }
        // IPv6 回环地址展示成 IPv4 形式，避免前端显示一串冒号
        if ("0:0:0:0:0:0:0:1".equals(ip) || "::1".equals(ip)) {
            return "127.0.0.1";
        }
        return ip;
    }
}
