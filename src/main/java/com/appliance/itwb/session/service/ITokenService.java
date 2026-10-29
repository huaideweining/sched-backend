package com.appliance.itwb.session.service;

/**
 * 令牌生成与哈希服务。
 *
 * <p><b>文件作用</b>：把"生成不可预测的令牌"和"不可逆地存储令牌"封装成两个能力，
 * 全系统只有这一处出现令牌原文与哈希的转换。</p>
 *
 * <p><b>依赖</b>：无（实现依赖 JDK 的 SecureRandom / MessageDigest）。</p>
 *
 * <p><b>Oracle 注意点</b>：哈希结果固定 64 个十六进制字符（SHA-256），
 * 与 TOKEN_HASH 列 VARCHAR2(128) 的容量完全匹配，不会触发 ORA-12899。</p>
 *
 * <p><b>关于 refreshToken</b>：不做独立的刷新令牌轮换机制。需要长期静默续期时，
 * 前端在剩余有效期低于阈值时调用「校验/续期」接口即可，语义等同 refresh，
 * 实现更小、状态更可控（见 LoginResponse 注释与 README）。</p>
 *
 * <p><b>安全约定</b>：明文令牌只存在于"客户端 → 服务端内存"这一段，
 * 不写库、不打日志、不进异常堆栈、不进审计表。</p>
 */
public interface ITokenService {

    /**
     * 生成业务会话号，对外暴露、全局唯一。
     *
     * @return 32 位十六进制字符串
     */
    String generateSessionId();

    /**
     * 生成访问令牌原文，仅返回给客户端，服务端只留哈希。
     *
     * @return 令牌原文（Base64URL 字符串）
     */
    String generateToken();

    /**
     * 计算令牌的 SHA-256 哈希（带服务端盐）。
     *
     * @param rawToken 令牌原文
     * @return 小写十六进制哈希串
     */
    String hashToken(String rawToken);

    /**
     * 校验令牌原文与库中哈希是否匹配，防止时序比较漏洞。
     *
     * @param rawToken    令牌原文
     * @param storedHash  库中记录的哈希
     * @return 一致返回 true
     */
    boolean matches(String rawToken, String storedHash);

    /**
     * 脱敏字符串，用于日志打印（如 abc*******xyz）。
     *
     * @param rawToken 待脱敏文本，可为 null
     * @return 脱敏后的文本
     */
    String mask(String rawToken);
}
