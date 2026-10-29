package com.witpath.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户个人大模型配置视图
 * <p>
 * 出于安全考虑，API Key 只返回掩码（前 6 后 4），前端不做明文回显；
 * 前端需要展示"已配置"状态时用 configured + apiKeyMasked 即可。
 * </p>
 */
@Data
public class UserAiConfigVO {

    private boolean configured;

    private String provider;

    private String baseUrl;

    private String model;

    private String apiKeyMasked;

    /* ---------------- 高级配置：向量模型 ---------------- */

    private String embeddingProvider;

    private String embeddingModel;

    private String embeddingBaseUrl;

    private String embeddingApiKeyMasked;

    /** 是否已覆盖系统默认的向量模型配置 */
    private boolean embeddingOverridden;

    /** 是否使用系统默认模型（true 时个人配置保留但不生效） */
    private boolean useDefaultApi;

    private LocalDateTime updatedAt;
}
