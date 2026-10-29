package com.witpath.service;

import com.witpath.ai.AiGateway;
import com.witpath.ai.DocIngestRequest;
import com.witpath.enumeration.ProcessStatus;
import com.witpath.mapper.StudyMaterialMapper;
import com.witpath.entity.StudyMaterial;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * 文档解析 / 向量化执行器
 * <p>
 * 由 RabbitMQ 消费者或本地降级通道调用，最终把请求转交给 FastAPI AI 服务完成：
 * 文本提取 -> 切分 -> Embedding -> 写入 Milvus。
 * 本类不反向依赖 MaterialService，避免出现循环依赖。
 * </p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MaterialIngestService {

    private final StudyMaterialMapper materialMapper;
    private final AiGateway aiGateway;

    /**
     * 执行资料解析与向量化，并回写处理状态
     */
    public void ingest(Long materialId, Long userId) {
        StudyMaterial material = materialMapper.selectById(materialId);
        if (material == null) {
            log.warn("资料不存在，跳过 ingest：materialId={}", materialId);
            return;
        }

        markStatus(materialId, ProcessStatus.PROCESSING.getCode(), ProcessStatus.PROCESSING.getCode(), null);
        try {
            DocIngestRequest request = new DocIngestRequest(
                    userId,
                    materialId,
                    material.getName(),
                    material.getFileType(),
                    material.getStorageType() != null && material.getStorageType() == 2 ? "oss" : "local",
                    material.getLocalPath(),
                    material.getOssBucket(),
                    material.getOssObjectKey(),
                    material.getSubject());

            JsonNode result = aiGateway.ingest(request);
            int chunks = result != null && result.has("chunk_count") ? result.get("chunk_count").asInt() : 0;
            markStatus(materialId, ProcessStatus.DONE.getCode(), ProcessStatus.DONE.getCode(), chunks);
            log.info("资料处理完成：materialId={} chunks={}", materialId, chunks);
        } catch (Exception e) {
            log.warn("资料处理失败：materialId={} msg={}", materialId, e.getMessage());
            markStatus(materialId, ProcessStatus.FAILED.getCode(), ProcessStatus.FAILED.getCode(), 0);
            StudyMaterial update = new StudyMaterial();
            update.setId(materialId);
            update.setErrorMessage(e.getMessage());
            materialMapper.updateById(update);
        }
    }

    private void markStatus(Long materialId, Integer parseStatus, Integer vectorStatus, Integer chunkCount) {
        StudyMaterial update = new StudyMaterial();
        update.setId(materialId);
        update.setParseStatus(parseStatus);
        update.setVectorStatus(vectorStatus);
        if (chunkCount != null) {
            update.setChunkCount(chunkCount);
        }
        materialMapper.updateById(update);
    }
}
