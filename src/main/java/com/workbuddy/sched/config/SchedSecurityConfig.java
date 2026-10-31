package com.workbuddy.sched.config;

import com.appliance.itwb.session.filter.TokenAuthenticationFilter;
import com.workbuddy.sched.filter.SchedTenantBridgeFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfigurationSource;

/**
 * 调度安全链（Spring Security 6 写法）。
 *
 * <p><b>统一登入方案（Phase B）</b>：撤销独立的 sched JWT 链，/api/sched/** 改为复用
 * 在役 wb 的 {@link TokenAuthenticationFilter}（同一 itwb 会话令牌），从而全站只存在
 * 唯一登入 URL {@code /api/auth/login} 与唯一令牌。本链仅负责：</p>
 * <ul>
 *   <li>① 以 {@code @Order(1)} 优先匹配 /api/sched/**，由 wb 会话令牌过滤器完成鉴权并写入
 *       {@link TokenAuthenticationFilter#ATTR_USER_ID}；</li>
 *   <li>② 由 {@link SchedTenantBridgeFilter} 把 {@code ATTR_USER_ID} 桥接进多租户
 *       {@code TenantContext}，保证 SCHED_TASK/SCHED_TODO 的行级隔离仍按当前用户生效；</li>
 *   <li>③ 过滤链只走 Security 链（不在 Servlet 容器级注册），避免污染 /api/wb/** 等非调度路径。</li>
 * </ul>
 */
@Configuration
@RequiredArgsConstructor
public class SchedSecurityConfig {

    /** 复用在役 wb 会话令牌过滤器（统一令牌，单一鉴权实现） */
    private final TokenAuthenticationFilter tokenAuthenticationFilter;
    /**
     * 复用在役 wb 的 CORS 配置（{@code itwb.session.cors-allowed-origins} 外部化的来源白名单，
     * 默认覆盖 8088 部署态 IP + 127.0.0.1 + localhost 三类来源；非 allow *，避免 Bearer 令牌透传裸奔）。
     * 与 {@code SecurityConfig} 共用同一个 {@link CorsConfigurationSource} Bean，改一处两边生效。
     */
    private final CorsConfigurationSource corsConfigurationSource;

    @Bean
    @Order(1)
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .securityMatcher("/api/sched/**")
            .csrf(AbstractHttpConfigurer::disable)
            .cors(cors -> cors.configurationSource(corsConfigurationSource))
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                // 健康/探针放开；其余 /api/sched/** 统一由 itwb 会话令牌保护
                .requestMatchers("/api/sched/actuator/**").permitAll()
                .anyRequest().authenticated()
            )
            // 先校验 itwb 会话令牌（写入 ATTR_USER_ID + SecurityContext），
            // 再由桥接过滤器把 userId 填入多租户 TenantContext
            .addFilterBefore(tokenAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
            .addFilterAfter(new SchedTenantBridgeFilter(), TokenAuthenticationFilter.class);
        return http.build();
    }
}
