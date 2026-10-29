package com.witpath.config;

import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.codec.JsonJacksonCodec;
import org.redisson.config.Config;
import org.redisson.config.TransportMode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Redisson 分布式客户端配置类
 * <p>
 * 基于 Spring 配置属性初始化 Redisson 单节点客户端，
 * 提供连接池、超时重试、编解码等生产级参数配置。
 * </p>
 */
@Configuration
public class RedissonConfig {

    @Value("${spring.data.redis.host:127.0.0.1}")
    private String host;

    @Value("${spring.data.redis.port:6379}")
    private int port;

    @Value("${spring.data.redis.password:}")
    private String password;

    @Value("${spring.data.redis.database:0}")
    private int database;

    @Value("${spring.data.redis.timeout:3000}")
    private int timeout;

    @Value("${spring.data.redis.pool.min-idle:2}")
    private int minIdleConnections;

    @Value("${spring.data.redis.pool.max-size:16}")
    private int maxPoolSize;

    @Value("${spring.data.redis.pool.max-wait:3000}")
    private int maxWaitMillis;

    @Value("${spring.redis.connection-minimum-idle-size:4}")
    private int connectionMinimumIdleSize;

    @Value("${spring.data.redis.connection-pool-size:16}")
    private int connectionPoolSize;

    @Value("${spring.data.redis.retry-attempts:3}")
    private int retryAttempts;

    @Value("${spring.data.redis.retry-interval:500}")
    private int retryInterval;

    /**
     * 创建 Redisson 单节点客户端实例
     *
     * @return RedissonClient 实例，应用关闭时自动 shutdown
     */
    @Bean(destroyMethod = "shutdown")
    public RedissonClient redissonClient() {
        Config config = new Config();

        // 使用 NIO 传输模式，兼容性最好
        config.setTransportMode(TransportMode.NIO);
        // 设置 JSON 编解码器，便于调试时查看序列化数据
        config.setCodec(new JsonJacksonCodec());
        // Netty 线程池数量，0 表示使用默认（CPU 核心数 * 2）
        config.setNettyThreads(0);
        config.setThreads(0);

        var singleServerConfig = config.useSingleServer()
                .setAddress("redis://" + host + ":" + port)
                .setDatabase(database)
                // 连接超时时间（毫秒）
                .setTimeout(timeout)
                // 命令失败重试次数
                .setRetryAttempts(retryAttempts)
                // 命令失败重试间隔（毫秒）
                .setRetryInterval(retryInterval)
                // 最小空闲连接数
                .setConnectionMinimumIdleSize(connectionMinimumIdleSize)
                // 连接池大小
                .setConnectionPoolSize(connectionPoolSize)
                // 从连接池获取连接的最大等待时间（毫秒）
                .setSubscriptionConnectionMinimumIdleSize(1)
                .setSubscriptionConnectionPoolSize(4);

        // 密码非空时设置认证
        if (password != null && !password.isEmpty()) {
            singleServerConfig.setPassword(password);
        }

        return Redisson.create(config);
    }
}