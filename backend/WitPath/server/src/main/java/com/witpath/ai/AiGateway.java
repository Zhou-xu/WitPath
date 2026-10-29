package com.witpath.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.witpath.dto.AiContextPayload;
import com.witpath.dto.UserAiRuntime;
import com.witpath.service.UserAiConfigService;
import com.witpath.ai.AiAnalyzeResponse;
import com.witpath.ai.AiChatResponse;
import com.witpath.ai.ChatRequest;
import com.witpath.ai.DocIngestRequest;
import com.witpath.exception.BusinessException;
import com.witpath.properties.AiServiceProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.time.Duration;

/**
 * AI 服务网关：Spring Boot 调用 FastAPI AI 服务的唯一出口
 * <p>
 * 业务后端只负责准备数据、传达结果，不做任何模型编排，避免 AI 逻辑耦合进 Controller。
 * </p>
 */
@Slf4j
@Component
public class AiGateway {

    private final AiServiceProperties properties;
    private final RestClient aiRestClient;
    private final UserAiConfigService userAiConfigService;

    public AiGateway(AiServiceProperties properties, RestClient aiRestClient,
                     UserAiConfigService userAiConfigService) {
        this.properties = properties;
        this.aiRestClient = aiRestClient;
        this.userAiConfigService = userAiConfigService;
    }

    /**
     * 取用户个人模型配置并注入请求
     * <p>
     * 任何异常（未配置 / 密钥轮换导致解密失败 / Redis 不可用）都降级为"不使用个人配置"，
     * 由 AI 服务回落到系统默认模型，绝不因此阻断 AI 主流程。
     * </p>
     */
    private void withUserConfig(Object request, Long userId) {
        if (request == null || userId == null) {
            return;
        }
        try {
            UserAiRuntime runtime = userAiConfigService.resolve(userId);
            if (runtime == null || (!runtime.hasLlm() && !runtime.hasEmbedding())) {
                return;
            }
            if (request instanceof AiContextPayload payload) {
                payload.setUserModelConfig(runtime);
            } else if (request instanceof ChatRequest chat) {
                chat.setUserModelConfig(runtime);
            } else if (request instanceof DocIngestRequest doc) {
                doc.setUserModelConfig(runtime);
            }
        } catch (Exception e) {
            log.warn("注入用户个人模型配置失败，本次使用系统默认模型：userId={} msg={}", userId, e.getMessage());
        }
    }

    /**
     * 发起一次学习分析 / 规划（LangGraph 多 Agent 工作流）
     */
    public AiAnalyzeResponse analyze(AiContextPayload payload) {
        withUserConfig(payload, payload == null ? null : payload.getUserId());
        try {
            return aiRestClient.post()
                    .uri("/api/ai/analyze")
                    .body(payload)
                    .retrieve()
                    .body(AiAnalyzeResponse.class);
        } catch (ResourceAccessException e) {
            throw new AiServiceException("AI 服务连接失败：" + e.getMessage());
        } catch (RestClientResponseException e) {
            throw new AiServiceException("AI 服务返回异常（" + e.getStatusCode().value() + "）：" + e.getResponseBodyAsString());
        }
    }

    /**
     * AI 助手多轮对话
     */
    public AiChatResponse chat(ChatRequest request) {
        withUserConfig(request, request == null ? null : request.getUserId());
        try {
            return aiRestClient.post()
                    .uri("/api/ai/chat")
                    .body(request)
                    .retrieve()
                    .body(AiChatResponse.class);
        } catch (ResourceAccessException e) {
            throw new AiServiceException("AI 服务连接失败：" + e.getMessage());
        } catch (RestClientResponseException e) {
            throw new AiServiceException("AI 服务返回异常（" + e.getStatusCode().value() + "）：" + e.getResponseBodyAsString());
        }
    }

    /**
     * 文档解析与向量化
     */
    public JsonNode ingest(DocIngestRequest request) {
        withUserConfig(request, request == null ? null : request.getUserId());
        try {
            return aiRestClient.post()
                    .uri("/api/ai/documents/ingest")
                    .body(request)
                    .retrieve()
                    .body(JsonNode.class);
        } catch (ResourceAccessException e) {
            throw new AiServiceException("AI 服务连接失败：" + e.getMessage());
        } catch (RestClientResponseException e) {
            throw new AiServiceException("AI 服务返回异常（" + e.getStatusCode().value() + "）：" + e.getResponseBodyAsString());
        }
    }

    /**
     * 知识检索（供前端直接检索资料片段）
     */
    public JsonNode search(Long userId, String query, int topK) {
        UserAiRuntime runtime = null;
        try {
            runtime = userAiConfigService.resolve(userId);
        } catch (Exception e) {
            log.warn("读取用户向量模型配置失败，使用系统默认：userId={} msg={}", userId, e.getMessage());
        }
        UserAiRuntime finalRuntime = runtime;
        try {
            // 走 POST + body：用户个人向量模型的 API Key 不能出现在 URL / 访问日志里
            return aiRestClient.post()
                    .uri("/api/ai/documents/search")
                    .body(new java.util.LinkedHashMap<String, Object>() {{
                        put("userId", userId);
                        put("query", query);
                        put("topK", topK);
                        if (finalRuntime != null && finalRuntime.hasEmbedding()) {
                            put("userModelConfig", finalRuntime);
                        }
                    }})
                    .retrieve()
                    .body(JsonNode.class);
        } catch (ResourceAccessException e) {
            throw new AiServiceException("AI 服务连接失败：" + e.getMessage());
        } catch (RestClientResponseException e) {
            throw new AiServiceException("AI 服务返回异常：" + e.getResponseBodyAsString());
        }
    }

    /**
     * 删除资料对应的向量（best-effort：AI 服务异常不影响资料本身删除）
     */
    public void deleteDocumentVectors(Long userId, Long materialId) {
        if (materialId == null) {
            return;
        }
        try {
            aiRestClient.delete()
                    .uri(uriBuilder -> uriBuilder.path("/api/ai/documents/{materialId}")
                            .queryParam("user_id", userId)
                            .build(materialId))
                    .retrieve()
                    .toBodilessEntity();
        } catch (Exception e) {
            // 向量清理失败仅记录日志，避免阻塞资料删除主流程
            log.warn("清理资料向量失败：materialId={} msg={}", materialId, e.getMessage());
        }
    }

    /**
     * 健康检查，返回原始 JSON（含模型、向量库、回退引擎等信息）
     */
    public JsonNode health() {
        return aiRestClient.get()
                .uri("/health")
                .retrieve()
                .body(JsonNode.class);
    }

    public String baseUrl() {
        return properties.getBaseUrl();
    }

    public String callbackBaseUrl() {
        return properties.getCallbackBaseUrl();
    }

    public String callbackToken() {
        return properties.getCallbackToken();
    }

    /**
     * AI 服务不可用异常（业务层据此标记任务失败，而非抛出 500）
     */
    public static class AiServiceException extends RuntimeException {
        public AiServiceException(String message) {
            super(message);
        }
    }
}
