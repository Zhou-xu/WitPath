package com.witpath.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.witpath.dto.UserAiRuntime;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 文档解析 / 向量化任务请求体（提交给 FastAPI）
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DocIngestRequest implements Serializable {

    private Long userId;

    private Long materialId;

    private String fileName;

    private String fileType;

    private String storageType;

    private String localPath;

    private String ossBucket;

    private String ossObjectKey;

    private String subject;

    /** 用户个人向量模型配置（高级配置，未填则为 null，AI 侧回落系统默认） */
    private UserAiRuntime userModelConfig;

    /** 兼容旧调用方：不携带个人模型配置（由 AiGateway 在发送前统一注入） */
    public DocIngestRequest(Long userId, Long materialId, String fileName, String fileType,
                            String storageType, String localPath, String ossBucket, String ossObjectKey,
                            String subject) {
        this(userId, materialId, fileName, fileType, storageType, localPath, ossBucket, ossObjectKey, subject, null);
    }
}
