package com.witpath.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 用户个人大模型配置
 * <p>
 * 明文 API Key 不落库：api_key_enc / embedding_api_key_enc 均为 AES-GCM 密文，
 * 密钥来自环境变量 APP_SECRET_KEY，见 com.witpath.properties.SecurityProperties。
 * </p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("user_api_key")
public class UserApiKey implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    /** 服务商标识：openai-compatible / ark / dashscope 等 */
    private String provider;

    /** 大模型 API Key（AES-GCM 密文） */
    private String apiKeyEnc;

    /** 大模型请求地址（OpenAI 兼容端点） */
    private String baseUrl;

    /** 选定的大模型标识 */
    private String model;

    /** 向量模型服务商（高级配置，留空表示不覆盖系统默认） */
    private String embeddingProvider;

    /** 向量模型标识 */
    private String embeddingModel;

    /** 向量模型 API Key（AES-GCM 密文） */
    private String embeddingApiKeyEnc;

    /** 向量模型请求地址 */
    private String embeddingBaseUrl;

    /** 状态：1启用 0禁用 */
    private Integer status;

    /**
     * 是否使用系统默认模型：1是 0否
     * <p>
     * 为 1 时该行配置不参与 AI 调用（AiGateway 不注入），系统走 .env 中的默认模型；
     * 配置本身保留，用户可随时改回个人配置。
     * </p>
     */
    private Integer useDefaultApi;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    private Integer isDeleted;
}
