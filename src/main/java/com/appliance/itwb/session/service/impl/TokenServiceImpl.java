package com.appliance.itwb.session.service.impl;

import com.appliance.itwb.session.config.TokenProperties;
import com.appliance.itwb.session.service.ITokenService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Locale;

/**
 * 令牌服务实现。
 *
 * <p><b>文件作用</b>：会话安全的核心。令牌必须是不可预测的随机数，存储必须是不可逆的哈希。</p>
 *
 * <p><b>依赖</b>：JDK 安全 API、{@link TokenProperties}。</p>
 *
 * <p><b>Oracle 注意点</b>：哈希值固定 64 字符，写入 TOKEN_HASH(VARCHAR2(128)) 不会越界。</p>
 *
 * <p><b>实现要点</b>：</p>
 * <ul>
 *   <li>令牌与会话号都用 SecureRandom 生成，ThreadLocalRandom 不适合做安全凭证；</li>
 *   <li>哈希 = SHA-256(服务端 pepper + 令牌原文)，pepper 不入库，拖库也还原不出明文；</li>
 *   <li>比对用 {@link MessageDigest#isEqual}，避免 ==/equals 的时序攻击；</li>
 *   <li>未配置 pepper 时自动生成随机值并打 WARN，避免用可预测的默认盐上线。</li>
 * </ul>
 */
@Service
public class TokenServiceImpl implements ITokenService {

    private static final Logger log = LoggerFactory.getLogger(TokenServiceImpl.class);

    /** 令牌/会话号的随机字节数。 */
    private static final int RANDOM_BYTES = 32;

    /** 十六进制字符表，保证哈希串全小写。 */
    private static final char[] HEX_CHARS = "0123456789abcdef".toCharArray();

    /** 安全随机数源。 */
    private final SecureRandom secureRandom = new SecureRandom();

    /** 服务端 pepper，只存在于内存与配置中心。 */
    private final String secret;

    /** 哈希算法名称。 */
    private final String hashAlgorithm;

    /**
     * 构造令牌服务。
     *
     * @param tokenProperties 令牌配置
     */
    public TokenServiceImpl(TokenProperties tokenProperties) {
        this.hashAlgorithm = StringUtils.hasText(tokenProperties.getHashAlgorithm())
                ? tokenProperties.getHashAlgorithm().toUpperCase(Locale.ROOT)
                : "SHA-256";
        if (StringUtils.hasText(tokenProperties.getSecret())) {
            this.secret = tokenProperties.getSecret();
        } else {
            // 未配置盐：随机生成，保证每次重启都不同，杜绝默认可推断的盐
            byte[] generated = new byte[RANDOM_BYTES];
            this.secureRandom.nextBytes(generated);
            this.secret = Base64.getUrlEncoder().withoutPadding().encodeToString(generated);
            log.warn("[session-server] itwb.token.secret 未配置，已自动生成随机盐；"
                    + "重启后旧令牌哈希将全部失效，生产环境请通过环境变量注入固定值。");
        }
    }

    @Override
    public String generateSessionId() {
        byte[] bytes = new byte[RANDOM_BYTES];
        secureRandom.nextBytes(bytes);
        StringBuilder sb = new StringBuilder(64);
        sb.append("sess_");
        appendHex(sb, bytes);
        return sb.toString();
    }

    @Override
    public String generateToken() {
        byte[] bytes = new byte[RANDOM_BYTES];
        secureRandom.nextBytes(bytes);
        // Base64URL 无 + / 字符，无需再次 URL 编码，适合放在 Header 与 Query 中
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    @Override
    public String hashToken(String rawToken) {
        return digest(rawToken, secret);
    }

    @Override
    public boolean matches(String rawToken, String storedHash) {
        if (!StringUtils.hasText(rawToken) || !StringUtils.hasText(storedHash)) {
            return false;
        }
        String actual = hashToken(rawToken);
        // 定长比较，恒定时间，避免通过响应时间侧信道逐字节爆破
        return MessageDigest.isEqual(actual.getBytes(StandardCharsets.UTF_8),
                storedHash.getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public String mask(String rawToken) {
        if (!StringUtils.hasText(rawToken)) {
            return "";
        }
        if (rawToken.length() <= 12) {
            return "***";
        }
        // 只保留头尾各 4 位，够排查又不泄露明文
        return rawToken.substring(0, 4) + "*******" + rawToken.substring(rawToken.length() - 4);
    }

    /**
     * 计算带盐哈希。
     *
     * @param value 待哈希文本，可为 null
     * @param salt  盐值
     * @return 64 位小写十六进制串
     */
    private String digest(String value, String salt) {
        if (value == null) {
            throw new IllegalArgumentException("哈希内容不能为 null");
        }
        try {
            MessageDigest digest = MessageDigest.getInstance(hashAlgorithm);
            // 盐在前、明文在后：固定结构，便于将来追加域参数（如 tokenId）而不改变接口
            digest.update(salt.getBytes(StandardCharsets.UTF_8));
            byte[] result = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(result.length * 2);
            appendHex(sb, result);
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("不支持的哈希算法: " + hashAlgorithm, e);
        }
    }

    /**
     * 字节数组转小写十六进制。
     *
     * @param sb  目标缓冲区
     * @param bytes 字节数组
     */
    private void appendHex(StringBuilder sb, byte[] bytes) {
        for (byte b : bytes) {
            sb.append(HEX_CHARS[(b >> 4) & 0x0F]).append(HEX_CHARS[b & 0x0F]);
        }
    }
}
