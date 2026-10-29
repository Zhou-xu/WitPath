package com.witpath.properties;

import lombok.Data;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * AI 服务（FastAPI）接入配置
 */
@Data
@Component
@ConfigurationProperties(prefix = "witpath.ai")
public class AiServiceProperties {

    /** AI 服务基础地址，例如 http://127.0.0.1:8000 */
    private String baseUrl = "http://127.0.0.1:8000";

    /** 同步分析接口超时时间（毫秒） */
    private long timeoutMs = 120_000L;

    /** 健康检查超时时间（毫秒） */
    private long healthTimeoutMs = 3_000L;

    /** AI 服务回调 Spring Boot 进度接口时携带的令牌 */
    private String callbackToken = "witpath-internal-token";

    /** 供 AI 服务回调的本服务地址（不包含路径），例如 http://127.0.0.1:8080 */
    private String callbackBaseUrl = "http://127.0.0.1:8080";

    /** 对接 AI 服务失败时是否抛错（false 表示降级为任务失败并记录原因） */
    private boolean failFast = false;

    private String apiKey;
}
