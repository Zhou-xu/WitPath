package com.witpath.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 异步执行器配置
 * <p>
 * 用于 RabbitMQ 不可用时的本地降级执行，以及 AI 任务的并行处理。
 * </p>
 */
@Configuration
@EnableScheduling
public class AsyncExecutorConfig {

    /**
     * AI 任务本地执行线程池（MQ 不可用时的降级通道）
     */
    @Bean("aiTaskExecutor")
    public ExecutorService aiTaskExecutor() {
        AtomicInteger idx = new AtomicInteger(1);
        ThreadFactory factory = r -> {
            Thread t = new Thread(r, "witpath-ai-task-" + idx.getAndIncrement());
            t.setDaemon(true);
            return t;
        };
        int core = Math.max(2, Runtime.getRuntime().availableProcessors() / 2);
        return new ThreadPoolExecutor(core, core * 2, 60L, TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(256), factory,
                new ThreadPoolExecutor.CallerRunsPolicy());
    }
}
