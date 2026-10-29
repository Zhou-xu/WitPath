package com.witpath.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 学习资料存储配置
 */
@Data
@Component
@ConfigurationProperties(prefix = "witpath.storage")
public class StorageProperties {

    /** 存储方式：local / oss */
    private String type = "local";

    /** 本地存储根目录（相对或绝对路径） */
    private String localDir = "./storage/materials";

    /** 单个文件大小上限（字节，默认 20MB） */
    private long maxFileSize = 20 * 1024 * 1024L;

    /** 允许上传的扩展名，留空表示不限制 */
    private String[] allowedExtensions = {"pdf", "doc", "docx", "md", "markdown", "txt", "csv", "ppt", "pptx"};

    private Oss oss = new Oss();

    private Avatar avatar = new Avatar();

    /**
     * 头像访问方式
     * <p>
     * proxy（默认）：bucket 保持私有，头像由后端 /api/user/avatar/raw 代理输出；
     * public：bucket 为公共读，直接返回 OSS 永久直链。
     * </p>
     */
    @Data
    public static class Avatar {
        /** proxy / public */
        private String accessMode = "proxy";
    }

    @Data
    public static class Oss {
        private String endpoint;
        private String accessKeyId;
        private String accessKeySecret;
        private String bucketName;
        /**
         * 头像专用 bucket（建议公共读，得到永久直链）；
         * 留空时退回 bucketName（此时头像与资料共用一个 bucket）。
         */
        private String avatarBucketName;
        /** 签名 URL 有效期（秒） */
        private long signatureTtlSeconds = 900L;
    }
}
