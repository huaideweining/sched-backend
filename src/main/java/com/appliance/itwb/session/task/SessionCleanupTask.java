package com.appliance.itwb.session.task;

import com.appliance.itwb.session.config.SessionProperties;
import com.appliance.itwb.session.service.ISysUserSessionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 过期会话清理定时任务。
 *
 * <p><b>文件作用</b>：把时间上已经过期的会话状态回收为 EXPIRED，
 * 让"在线终端列表"和"会话数上限统计"看到的是真实有效的会话。</p>
 *
 * <p><b>依赖</b>：spring-context（@Scheduled）、{@link ISysUserSessionService}。</p>
 *
 * <p><b>Oracle 注意点</b>：清理 SQL 用 ROWNUM 分批，避免一次锁住过多行造成阻塞；
 * 幂等，重复执行没有副作用；EXPIRE_TIME 上有单列索引，范围扫描代价可控。</p>
 *
 * <p><b>频率建议</b>：默认 10 分钟一次（{@code itwb.session.cleanup-cron}）。
 * 频率再高收益不明显（校验逻辑本身已按 EXPIRE_TIME 判死，不依赖状态字段）。
 * 若数据库侧已用 DBMS_SCHEDULER 跑 PRG_CLEAN_EXPIRE_SESSION，请把
 * {@code itwb.session.cleanup-enabled} 设为 false，避免两边重复清理。</p>
 *
 * <p><b>启用方式</b>：在启动类加 {@code @EnableScheduling}，本类在
 * {@code cleanup-enabled=true} 时生效。</p>
 */
@Component
@ConditionalOnProperty(name = "itwb.session.cleanup-enabled", havingValue = "true", matchIfMissing = true)
public class SessionCleanupTask {

    private static final Logger log = LoggerFactory.getLogger(SessionCleanupTask.class);

    private static final int DEFAULT_RETENTION_DAYS = 180;

    private final ISysUserSessionService sessionService;
    private final SessionProperties sessionProperties;

    /**
     * 构造清理任务。
     *
     * @param sessionService  会话服务
     * @param sessionProperties 会话配置
     */
    public SessionCleanupTask(ISysUserSessionService sessionService,
                              SessionProperties sessionProperties) {
        this.sessionService = sessionService;
        this.sessionProperties = sessionProperties;
    }

    /**
     * 定时回收过期会话。
     *
     * <p>cron 从 {@code itwb.session.cleanup-cron} 读取，默认每 10 分钟一次。</p>
     */
    @Scheduled(cron = "${itwb.session.cleanup-cron:0 0/10 * * * ?}")
    public void cleanupExpiredSessions() {
        long start = System.currentTimeMillis();
        int rows = sessionService.cleanupExpiredSessions();
        int deleted = sessionService.deleteRetainedSessions(DEFAULT_RETENTION_DAYS);
        if (rows > 0 || deleted > 0) {
            log.info("[session-cleanup] 回收过期会话 rows={}, 物理删除历史记录 rows={}, 耗时={}ms",
                    rows, deleted, System.currentTimeMillis() - start);
        }
    }
}
