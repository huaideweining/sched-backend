package com.appliance.itwb.session.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 令牌与哈希配置。
 *
 * <p><b>文件作用</b>：集中管理令牌哈希的盐值与算法，避免盐值硬编码在代码里被反编译泄露。</p>
 *
 * <p><b>依赖</b>：spring-boot（@ConfigurationProperties）。</p>
 *
 * <p><b>盐值策略</b>：采用"服务端固定 pepper + 不入库"的方案。</p>
 * <ul>
 *   <li>hash = SHA-256(pepper + 明文令牌)，pepper 来自配置或首次启动随机生成；</li>
 *   <li>令牌本身 32 字节安全随机数，本身已足够随机，无需再加逐令牌随机盐（加盐只防彩虹表，
 *       而随机令牌 rainbow table 无从构造）；</li>
 *   <li>若将来要支持"令牌可吊销且跨服务共享"，可改为 hash = SHA-256(pepper + tokenId + token)，
 *       并把盐一起入库，代价是多一个字段与一次等值查询。</li>
 * </ul>
 *
 * <p><b>Oracle 注意点</b>：无。</p>
 *
 * <p><b>yml 示例</b>：</p>
 * <pre>
 * itwb:
 *   token:
 *     secret: ${ITWB_TOKEN_SECRET:}   # 生产环境务必用环境变量注入，勿提交到代码库
 *     hash-algorithm: SHA-256
 * </pre>
 */
@Component
@ConfigurationProperties(prefix = "itwb.token")
public class TokenProperties {

    /** 服务端 pepper，用于给令牌哈希加盐；为空时首次启动自动生成随机值并告警。 */
    private String secret;

    /** 哈希算法，默认 SHA-256（不可逆，无法从哈希还原令牌）。 */
    private String hashAlgorithm = "SHA-256";

    public String getSecret() {
        return secret;
    }

    public void setSecret(String secret) {
        this.secret = secret;
    }

    public String getHashAlgorithm() {
        return hashAlgorithm;
    }

    public void setHashAlgorithm(String hashAlgorithm) {
        this.hashAlgorithm = hashAlgorithm;
    }
}
