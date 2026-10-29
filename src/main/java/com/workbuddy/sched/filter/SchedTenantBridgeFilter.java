package com.workbuddy.sched.filter;

import com.appliance.itwb.session.filter.TokenAuthenticationFilter;
import com.workbuddy.sched.common.TenantContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * 多租户桥接过滤器（Phase B 统一登入方案）。
 *
 * <p><b>作用</b>：把 itwb 会话令牌过滤器（{@link TokenAuthenticationFilter}）在请求属性上写入的
 * {@code ATTR_USER_ID} 桥接进多租户 {@link TenantContext}，使得 SCHED_TASK / SCHED_TODO 的
 * 行级隔离（{@code MybatisPlusConfig.TenantLineHandler}）仍按"当前登录用户"生效。</p>
 *
 * <p><b>与后台调度线程的关系</b>：{@code JobExecutor} 在执行触发时会自行
 * {@code TenantContext.set(trigger.getUserId())}，后台线程不经过本过滤器，二者互不干扰。
 * 本过滤器只在 HTTP 请求线程内、且已成功解析出有效用户时设置 TenantContext，并在本次
 * 请求链路结束时清除，避免容器线程复用导致身份串号。</p>
 *
 * <p><b>为什么不直接让 Controller 读 ATTR_USER_ID</b>：MyBatis 多租户拦截器只能从
 * {@code TenantContext}（ThreadLocal）取 userId，无法读 HttpServletRequest；统一由本过滤器
 * 注入 TenantContext，可让 Controller / Service / 拦截器共用同一来源，无需双写。</p>
 */
public class SchedTenantBridgeFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        Object userId = request.getAttribute(TokenAuthenticationFilter.ATTR_USER_ID);
        if (userId instanceof String uid && !uid.isEmpty()) {
            TenantContext.set(uid);
            try {
                filterChain.doFilter(request, response);
            } finally {
                // 请求结束清除，防止线程复用串号
                TenantContext.clear();
            }
        } else {
            // 兜底：理论上不可达 —— TokenAuthenticationFilter 已拦截无令牌请求并写 401。
            // 此处不设置 TenantContext，直接放行交由授权层统一拒绝。
            filterChain.doFilter(request, response);
        }
    }
}
