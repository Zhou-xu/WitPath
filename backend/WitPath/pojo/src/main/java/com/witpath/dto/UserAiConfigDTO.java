package com.witpath.dto;

import lombok.Data;

/**
 * 保存用户个人大模型配置入参
 * <p>
 * apiKey 为明文，仅在请求链路的内存中出现，落库前由 AES-GCM 加密。
 * </p>
 */
@Data
public class UserAiConfigDTO {

    /** 服务商标识：openai-compatible（默认）/ ark / dashscope 等 */
    private String provider = "openai-compatible";

    /** 大模型 API Key（明文） */
    private String apiKey;

    /** 大模型请求地址，如 https://ark.cn-beijing.volces.com/api/v3 */
    private String baseUrl;

    /** 选定的模型标识（前端从「获取模型」列表中选取，必填） */
    private String model;

    /* ---------------- 高级配置：向量模型（留空表示不覆盖系统默认） ---------------- */

    private String embeddingProvider;

    private String embeddingModel;

    private String embeddingApiKey;

    private String embeddingBaseUrl;

    /** 保存后是否切换回系统默认模型：true 时该配置保留但不生效 */
    private Boolean useDefaultApi;
}
