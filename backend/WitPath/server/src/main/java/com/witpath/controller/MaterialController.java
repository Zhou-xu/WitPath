package com.witpath.controller;

import com.witpath.context.UserContext;
import com.witpath.dto.MaterialUpdateDTO;
import com.witpath.dto.PageQueryDTO;
import com.witpath.entity.StudyMaterial;
import com.witpath.enumeration.ProcessStatus;
import com.witpath.exception.BusinessException;
import com.witpath.properties.StorageProperties;
import com.witpath.result.PageResult;
import com.witpath.result.Result;
import com.witpath.service.MaterialService;
import com.witpath.storage.StorageService;
import com.witpath.vo.MaterialVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * 学习资料控制器：上传、列表、下载、重新向量化
 */
@Slf4j
@RestController
@RequestMapping("/api/materials")
@RequiredArgsConstructor
public class MaterialController {

    private final MaterialService materialService;
    private final StorageService storageService;
    private final StorageProperties storageProperties;

    @GetMapping("/page")
    public Result<PageResult<MaterialVO>> page(PageQueryDTO query,
                                               @RequestParam(value = "subject", required = false) String subject) {
        return Result.success(materialService.page(UserContext.requireUserId(), query, subject));
    }

    /**
     * 上传学习资料
     */
    @PostMapping("/upload")
    public Result<MaterialVO> upload(@RequestPart("file") MultipartFile file,
                                     @RequestParam(value = "subject", required = false) String subject,
                                     @RequestParam(value = "description", required = false) String description) {
        Long userId = UserContext.requireUserId();
        if (file == null || file.isEmpty()) {
            throw new BusinessException(400, "请选择要上传的文件");
        }
        if (file.getSize() > storageProperties.getMaxFileSize()) {
            throw new BusinessException(400, "文件大小超过上限（"
                    + (storageProperties.getMaxFileSize() / 1024 / 1024) + "MB）");
        }

        String originalName = file.getOriginalFilename() == null ? "unnamed" : file.getOriginalFilename();
        String ext = extractExt(originalName);
        if (!isAllowed(ext)) {
            throw new BusinessException(400, "不支持的文件类型：" + ext);
        }

        String displayName = originalName.contains(".") ? originalName : originalName + "." + ext;
        String objectKey = "u" + userId + "/" + LocalDate.now() + "/" + UUID.randomUUID() + "." + ext;

        try (InputStream in = file.getInputStream()) {
            storageService.save(in, objectKey);
        } catch (Exception e) {
            log.error("文件存储失败：{}", e.getMessage(), e);
            throw new BusinessException(500, "文件存储失败：" + e.getMessage());
        }

        boolean isOss = "oss".equalsIgnoreCase(storageProperties.getType());
        StudyMaterial material = StudyMaterial.builder()
                .userId(userId)
                .name(displayName)
                .originalName(originalName)
                .fileType(ext)
                .storageType(isOss ? 2 : 1)
                .localPath(isOss ? null : objectKey)
                .ossBucket(isOss ? storageProperties.getOss().getBucketName() : null)
                .ossObjectKey(isOss ? objectKey : null)
                .ossUrl(isOss ? storageService.resolveUrl(objectKey) : null)
                .fileSize(file.getSize())
                .subject(subject)
                .description(description)
                .parseStatus(ProcessStatus.INIT.getCode())
                .vectorStatus(ProcessStatus.INIT.getCode())
                .chunkCount(0)
                .isDeleted(0)
                .build();

        MaterialVO vo = materialService.create(material);
        // 触发异步解析与向量化
        materialService.requestIngest(material.getId(), userId);
        return Result.success(vo);
    }

    @PutMapping("/{id}")
    public Result<MaterialVO> update(@PathVariable Long id, @RequestBody MaterialUpdateDTO dto) {
        return Result.success(materialService.update(UserContext.requireUserId(), id, dto));
    }

    @DeleteMapping("/{id}")
    public Result<Boolean> remove(@PathVariable Long id) {
        materialService.remove(UserContext.requireUserId(), id);
        return Result.success(true);
    }

    /**
     * 重新触发解析与向量化
     */
    @PostMapping("/{id}/ingest")
    public Result<Boolean> ingest(@PathVariable Long id) {
        Long userId = UserContext.requireUserId();
        materialService.getOwned(userId, id);
        materialService.requestIngest(id, userId);
        return Result.success(true);
    }

    private String extractExt(String filename) {
        int idx = filename.lastIndexOf('.');
        return idx < 0 ? "" : filename.substring(idx + 1).toLowerCase();
    }

    private boolean isAllowed(String ext) {
        String[] allowed = storageProperties.getAllowedExtensions();
        if (allowed == null || allowed.length == 0) {
            return true;
        }
        for (String item : allowed) {
            if (item.equalsIgnoreCase(ext)) {
                return true;
            }
        }
        return false;
    }
}
