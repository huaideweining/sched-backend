package com.workbuddy.sched.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * R4 鉴权相关 Bean 配置。
 *
 * <p>声明全局唯一的 {@link PasswordEncoder}（BCrypt），供注册时对明文密码做哈希，
 * 与既有 SCHED_USER 手插 BCrypt 哈希保持同构。全工程无其它 PasswordEncoder Bean，
 * 故不会触发 Spring 的 NoUniqueBeanDefinitionException。</p>
 */
@Configuration
public class SchedAuthConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        // strength 默认 10，与现有 BCrypt 哈希强度一致
        return new BCryptPasswordEncoder();
    }
}
