package com.workbuddy.sched.common;

/**
 * 当前请求的用户上下文（多租户隔离）。由 JWT 过滤器在请求入口设置，响应后清除。
 */
public final class TenantContext {
    private static final ThreadLocal<String> USER_ID = new ThreadLocal<>();

    public static void set(String userId) {
        USER_ID.set(userId);
    }

    public static String get() {
        return USER_ID.get();
    }

    public static void clear() {
        USER_ID.remove();
    }

    private TenantContext() {}
}
