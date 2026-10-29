package com.witpath.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 学习资料实体（仅保存元数据，实体文件存放于本地磁盘或 OSS）
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("study_material")
public class StudyMaterial implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private String name;

    private String originalName;

    private String fileType;

    /** 存储类型：1 本地磁盘 2 阿里云OSS */
    private Integer storageType;

    private String localPath;

    private String ossBucket;

    private String ossObjectKey;

    private String ossUrl;

    private Long fileSize;

    private String subject;

    private String description;

    /** 解析状态：见 com.witpath.enumeration.ProcessStatus */
    private Integer parseStatus;

    /** 向量化状态：见 com.witpath.enumeration.ProcessStatus */
    private Integer vectorStatus;

    private Integer chunkCount;

    private String errorMessage;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    private Integer isDeleted;
}
