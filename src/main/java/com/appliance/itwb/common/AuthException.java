package com.appliance.itwb.common;

/**
 * 认证失败异常（用户名或密码错误）。
 *
 * <p><b>文件作用</b>：把"认证失败"与"系统异常"区分开——认证失败返回 401 与明确提示，
 * 而不是兜底的 500"服务异常"，避免前端无法区分"密码错了"和"后端挂了"。</p>
 *
 * <p><b>依赖</b>：无。</p>
 *
 * <p><b>Oracle 注意点</b>：无。</p>
 */
public class AuthException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public AuthException(String message) {
        super(message);
    }
}
