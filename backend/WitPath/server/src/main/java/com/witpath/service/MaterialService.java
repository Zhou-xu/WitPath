package com.witpath.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.witpath.ai.AiGateway;
import com.witpath.ai.DocIngestRequest;
import com.witpath.dto.PageQueryDTO;
import com.witpath.entity.StudyMaterial;
import com.witpath.enumeration.ProcessStatus;
import com.witpath.mapper.StudyMaterialMapper;
import com.witpath.mq.DocIngestMessage;
import com.witpath.mq.TaskDispatcher;
import com.witpath.dto.MaterialUpdateDTO;
import com.witpath.exception.BusinessException;
import com.witpath.result.PageResult;
import com.witpath.storage.StorageService;
import com.witpath.support.VoConverter;
import com.witpath.vo.MaterialVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

/**
 * 学习资料管理
 * <p>
 * 文件实体由 {@link StorageService} 落地（本地磁盘或 OSS），MySQL 仅保存元数据。
 * 文档解析与向量化交由 AI 服务异步处理，由 {@link #requestIngest} 触发消息投递。
 * </p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MaterialService {

    private final StudyMaterialMapper materialMapper;
    private final StorageService storageService;
    private final AiGateway aiGateway;
    private final TaskDispatcher taskDispatcher;

    public PageResult<MaterialVO> page(Long userId, PageQueryDTO query, String subject) {
        Page<StudyMaterial> page = new Page<>(query.safePage(), query.safeSize());
        LambdaQueryWrapper<StudyMaterial> wrapper = new LambdaQueryWrapper<StudyMaterial>()
                .eq(StudyMaterial::getUserId, userId)
                .eq(subject != null && !subject.isBlank(), StudyMaterial::getSubject, subject)
                .orderByDesc(StudyMaterial::getId);
        materialMapper.selectPage(page, wrapper);

        List<MaterialVO> vos = page.getRecords().stream()
                .map(m -> VoConverter.toMaterialVO(m, resolveUrl(m)))
                .toList();
        return PageResult.of(page.getCurrent(), page.getSize(), page.getTotal(), vos);
    }

    /**
     * 保存上传的资料元数据
     */
    public MaterialVO create(StudyMaterial material) {
        materialMapper.insert(material);
        return VoConverter.toMaterialVO(material, resolveUrl(material));
    }

    /**
     * 按存储类型取对象键并生成访问 URL。
     * <p>
     * 本地存储只写 local_path、OSS 存储只写 oss_object_key，
     * 直接取其中一个字段会得到 null，进而导致 URL 拼接 NPE。
     * </p>
     */
    private String resolveUrl(StudyMaterial material) {
        if (material == null) {
            return "";
        }
        boolean oss = material.getStorageType() != null && material.getStorageType() == 2;
        String key = oss ? material.getOssObjectKey() : material.getLocalPath();
        if (key == null || key.isBlank()) {
            return "";
        }
        return storageService.resolveUrl(key);
    }

    public MaterialVO update(Long userId, Long id, MaterialUpdateDTO dto) {
        StudyMaterial material = getOwned(userId, id);
        StudyMaterial update = new StudyMaterial();
        update.setId(id);
        update.setName(dto.getName());
        update.setSubject(dto.getSubject());
        update.setDescription(dto.getDescription());
        materialMapper.updateById(update);
        return VoConverter.toMaterialVO(materialMapper.selectById(id), resolveUrl(material));
    }

    public void remove(Long userId, Long id) {
        StudyMaterial material = getOwned(userId, id);
        materialMapper.deleteById(id);
        if (material.getStorageType() != null && material.getStorageType() == 2) {
            if (material.getOssObjectKey() != null) {
                storageService.delete(material.getOssObjectKey());
            }
        } else if (material.getLocalPath() != null) {
            storageService.delete(material.getLocalPath());
        }
        // 同步清理向量，避免资料删除后仍被 AI 检索到
        aiGateway.deleteDocumentVectors(material.getUserId(), id);
    }

    public StudyMaterial getOwned(Long userId, Long id) {
        StudyMaterial material = materialMapper.selectById(id);
        if (material == null || !Objects.equals(material.getUserId(), userId)) {
            throw new BusinessException(404, "资料不存在或无权访问");
        }
        return material;
    }

    /**
     * 触发文档解析与向量化（投递到 RabbitMQ，不可用时本地降级）
     */
    public void requestIngest(Long materialId, Long userId) {
        taskDispatcher.dispatchDocIngest(new DocIngestMessage(materialId, userId));
    }

    /**
     * 重置处理状态，便于资料重新解析
     */
    public void markProcessing(Long materialId) {
        StudyMaterial update = new StudyMaterial();
        update.setId(materialId);
        update.setParseStatus(ProcessStatus.PROCESSING.getCode());
        update.setVectorStatus(ProcessStatus.PROCESSING.getCode());
        update.setErrorMessage(null);
        materialMapper.updateById(update);
    }

    /**
     * 直接调用 AI 服务重新 ingest（失败时记录原因）
     */
    public void ingestDirect(Long userId, Long materialId) {
        StudyMaterial material = materialMapper.selectById(materialId);
        if (material == null) {
            return;
        }
        markProcessing(materialId);
        try {
            aiGateway.ingest(new DocIngestRequest(
                    userId,
                    materialId,
                    material.getName(),
                    material.getFileType(),
                    material.getStorageType() != null && material.getStorageType() == 2 ? "oss" : "local",
                    material.getLocalPath(),
                    material.getOssBucket(),
                    material.getOssObjectKey(),
                    material.getSubject()));
        } catch (Exception e) {
            StudyMaterial update = new StudyMaterial();
            update.setId(materialId);
            update.setParseStatus(ProcessStatus.FAILED.getCode());
            update.setVectorStatus(ProcessStatus.FAILED.getCode());
            update.setErrorMessage(e.getMessage());
            materialMapper.updateById(update);
        }
    }

    /**
     * 统计用户的资料数量
     */
    public long countByUser(Long userId) {
        Long count = materialMapper.selectCount(
                new LambdaQueryWrapper<StudyMaterial>().eq(StudyMaterial::getUserId, userId));
        return count == null ? 0L : count;
    }

    /**
     * 标记处理完成
     */
    public void markIngested(Long materialId, Integer chunkCount) {
        StudyMaterial update = new StudyMaterial();
        update.setId(materialId);
        update.setParseStatus(ProcessStatus.DONE.getCode());
        update.setVectorStatus(ProcessStatus.DONE.getCode());
        update.setChunkCount(chunkCount == null ? 0 : chunkCount);
        update.setErrorMessage(null);
        materialMapper.updateById(update);
    }
}
