package com.witpath.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.witpath.ai.AiGateway;
import com.witpath.config.AsyncExecutorConfig;
import com.witpath.mq.TaskDispatcher;
import com.witpath.properties.StorageProperties;
import com.witpath.result.Result;
import com.witpath.utils.JsonUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 对外健康检查与系统状态
 */
@Slf4j
@RestController
@RequestMapping("/public")
@RequiredArgsConstructor
public class PublicController {

    private final AiGateway aiGateway;
    private final TaskDispatcher taskDispatcher;
    private final StorageProperties storageProperties;

    @Value("${spring.application.name:server}")
    private String appName;

    /**
     * 聚合健康状态：后端自身 + AI 服务 + 消息队列 + 存储方式
     */
    @GetMapping("/health")
    public Result<JsonNode> health() {
        ObjectNode node = JsonUtils.mapper().createObjectNode();
        node.put("service", appName);
        node.put("backend", "UP");
        node.put("storage", storageProperties.getType());
        node.put("mq", taskDispatcher.isBrokerAvailable() ? "UP" : "DEGRADED");

        try {
            JsonNode ai = aiGateway.health();
            node.put("ai", "UP");
            node.set("aiDetail", ai);
        } catch (Exception e) {
            node.put("ai", "DOWN");
            node.put("aiError", e.getMessage());
        }
        return Result.success(node);
    }
}
