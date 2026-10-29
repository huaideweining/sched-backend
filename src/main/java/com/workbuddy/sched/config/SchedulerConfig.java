package com.workbuddy.sched.config;

import com.workbuddy.sched.scheduler.ComputeNextRun;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Executors;

/**
 * 调度内核 Bean 配置。
 * 断点建议：在 SchedulerBootstrap 的启动装载处下断，验证参数注入与线程池。
 */
@Configuration
public class SchedulerConfig {

    @Value("${scheduler.wheel-tick-ms:1000}")
    private long wheelTickMs;

    @Value("${scheduler.dispatch-interval-ms:2000}")
    private long dispatchIntervalMs;

    @Value("${scheduler.misfire-threshold-ms:300000}")
    private long misfireThresholdMs;

    @Value("${scheduler.catch-up-max:1}")
    private int catchUpMax;

    @Value("${scheduler.shard-total:1}")
    private int shardTotal;

    @Bean
    public ComputeNextRun computeNextRun() {
        return new ComputeNextRun();
    }

    /** 时间轮专用单线程调度器（保证触发顺序） */
    @Bean("schedWheelExecutor")
    public ScheduledExecutorService schedWheelExecutor() {
        return Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "sched-wheel");
            t.setDaemon(true);
            return t;
        });
    }

    /** Job 执行线程池（生成待办 / 重试 / 补算） */
    @Bean("schedJobExecutor")
    public Executor schedJobExecutor() {
        ThreadPoolTaskExecutor ex = new ThreadPoolTaskExecutor();
        ex.setCorePoolSize(4);
        ex.setMaxPoolSize(16);
        ex.setQueueCapacity(200);
        ex.setThreadNamePrefix("sched-job-");
        ex.initialize();
        return ex;
    }

    public long getWheelTickMs() { return wheelTickMs; }
    public long getDispatchIntervalMs() { return dispatchIntervalMs; }
    public long getMisfireThresholdMs() { return misfireThresholdMs; }
    public int getCatchUpMax() { return catchUpMax; }
    public int getShardTotalValue() { return shardTotal; }
}
