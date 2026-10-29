package com.witpath.vo;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 学习资料 VO
 */
@Data
public class MaterialVO implements Serializable {

    private Long id;

    private String name;

    private String originalName;

    private String fileType;

    private Long fileSize;

    private String fileSizeText;

    /** 存储类型：1 本地 2 OSS */
    private Integer storageType;

    private String storageTypeText;

    private String subject;

    private String description;

    private Integer parseStatus;

    private String parseStatusText;

    private Integer vectorStatus;

    private String vectorStatusText;

    private Integer chunkCount;

    private String errorMessage;

    /** 下载地址（本地为 /files/...，OSS 为签名 URL） */
    private String url;

    private LocalDateTime createdAt;
}
