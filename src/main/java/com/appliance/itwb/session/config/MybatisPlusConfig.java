package com.appliance.itwb.session.config;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.handler.TenantLineHandler;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.TenantLineInnerInterceptor;
import com.workbuddy.sched.common.TenantContext;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.StringValue;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * MyBatis-Plus 插件配置（合并在役 + 方案③调度）。
 *
 * <p><b>文件作用</b>：</p>
 * <ul>
 *   <li>① 多租户行级隔离（方案③）：自动在 SCHED_TASK / SCHED_TODO 查询追加 USER_ID 谓词；
 *       其余表（WB_* / SCHED_TRIGGER / SCHED_FIRE_LOG / SCHED_USER / SCHED_NODE）忽略，
 *       由显式 userId 查询或无需隔离保证正确性；</li>
 *   <li>② 分页插件：Oracle 方言，单页上限 1000，溢出返回空页。</li>
 * </ul>
 */
@Configuration
public class MybatisPlusConfig {

    /**
     * 需要自动行级隔离的表（用户侧查询）。
     * 注意：SCHED_TRIGGER / SCHED_FIRE_LOG 由调度器跨用户访问，不在此列，
     * 其隔离由「按 triggerId / userId 显式查询」保证，避免自动注入 USER_ID 谓词破坏分片抢占。
     */
    private static final List<String> TENANT_TABLES = List.of(
            "SCHED_TASK", "SCHED_TODO"
    );

    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();

        // ① 多租户行级隔离
        interceptor.addInnerInterceptor(new TenantLineInnerInterceptor(new TenantLineHandler() {
            @Override
            public Expression getTenantId() {
                // SCHED_NODE / SCHED_USER 无 USER_ID，由 ignoreTable 放行
                return new StringValue(TenantContext.get() == null ? "" : TenantContext.get());
            }

            @Override
            public String getTenantIdColumn() {
                return "USER_ID";
            }

            @Override
            public boolean ignoreTable(String tableName) {
                return !TENANT_TABLES.contains(tableName);
            }
        }));

        // ② 分页（Oracle 方言）
        PaginationInnerInterceptor paginationInterceptor = new PaginationInnerInterceptor(DbType.ORACLE);
        // 单页最大条数上限，超过则按上限截断，防止大结果集
        paginationInterceptor.setMaxLimit(1000L);
        // 页码溢出时返回空页而不是抛异常
        paginationInterceptor.setOverflow(true);
        interceptor.addInnerInterceptor(paginationInterceptor);

        return interceptor;
    }
}
