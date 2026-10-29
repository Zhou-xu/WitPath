package com.witpath.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 提交给 AI 服务的用户个人模型配置（运行时）
 * <p>
 * 字段为 camelCase，AI 侧 CamelModel 会自动映射到 llm_api_key / llm_base_url 等 snake_case 字段。
 * 未配置的能力传空串/空值，AI 侧据此回落服务端 .env 中的默认配置。
 * </p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserAiRuntime implements Serializable {

    private String llmApiKey;

    private String llmBaseUrl;

    private String llmModel;

    private String llmProvider;

    /* ---------------- 向量模型（高级配置） ---------------- */

    private String embeddingProvider;

    private String embeddingModel;

    private String embeddingApiKey;

    private String embeddingBaseUrl;

    public static UserAiRuntime empty() {
        return UserAiRuntime.builder().build();
    }

    public boolean hasLlm() {
        return notBlank(llmApiKey) && notBlank(llmBaseUrl) && notBlank(llmModel);
    }

    public boolean hasEmbedding() {
        return notBlank(embeddingApiKey) && notBlank(embeddingModel);
    }

    private static boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }
}
