package com.witpath.service;

import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSClientBuilder;
import com.aliyun.oss.model.OSSObject;
import com.aliyun.oss.model.ObjectMetadata;
import com.aliyun.oss.model.PutObjectRequest;
import com.witpath.exception.BusinessException;
import com.witpath.properties.StorageProperties;
import com.witpath.support.AvatarPresets;
import com.witpath.vo.AvatarPresetVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 头像服务：本地上传与内置在线头像，统一写入阿里云 OSS（公共读）
 * <p>
 * 与资料存储解耦：头像使用独立的公共读 bucket（witpath.storage.oss.avatar-bucket-name），
 * 落库的是 OSS 永久直链，不依赖签名 URL（签名 URL 过期后头像会失效）；
 * 若 bucket 不可公共读，自动退化为后端代理地址 /api/user/avatar/raw。
 * </p>
 */
@Slf4j
@Service
public class AvatarService {

    /** 头像体积上限：5MB（远小于资料文件上限，避免大图拖慢个人页） */
    private static final long MAX_SIZE = 5 * 1024 * 1024L;

    /** 允许的图片格式；不含 svg，避免上传任意 SVG 带来的 XSS 风险 */
    private static final Set<String> ALLOWED_EXT = Set.of("jpg", "jpeg", "png", "webp", "gif", "bmp");

    private static final Map<String, String> CONTENT_TYPES = Map.of(
            "jpg", "image/jpeg",
            "jpeg", "image/jpeg",
            "png", "image/png",
            "webp", "image/webp",
            "gif", "image/gif",
            "bmp", "image/bmp",
            "svg", "image/svg+xml");

    private static final String PREFIX = "avatars";

    /** 私有 bucket 场景下由后端代理输出头像（免鉴权，仅限 avatars/ 前缀） */
    public static final String PROXY_PATH = "/api/user/avatar/raw";

    private final StorageProperties properties;

    private volatile OSS client;

    public AvatarService(StorageProperties properties) {
        this.properties = properties;
    }

    /** 内置在线头像列表（含 SVG 源码，前端可直接预览） */
    public List<AvatarPresetVO> presets() {
        return AvatarPresets.toVOList();
    }

    /**
     * 上传本地图片作为头像，返回可长期访问的头像地址
     */
    public String upload(Long userId, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(400, "请选择要上传的图片");
        }
        if (file.getSize() > MAX_SIZE) {
            throw new BusinessException(400, "图片不能超过 5MB");
        }
        String ext = extractExt(file.getOriginalFilename());
        if (!ALLOWED_EXT.contains(ext)) {
            throw new BusinessException(400, "仅支持 jpg / png / webp / gif / bmp 图片");
        }

        byte[] bytes;
        try (InputStream in = file.getInputStream()) {
            bytes = in.readAllBytes();
        } catch (IOException e) {
            log.error("读取头像文件失败：{}", e.getMessage(), e);
            throw new BusinessException(400, "图片读取失败");
        }

        String key = PREFIX + "/u" + userId + "/" + LocalDate.now() + "/" + UUID.randomUUID() + "." + ext;
        return store(key, bytes, CONTENT_TYPES.get(ext));
    }

    /**
     * 使用内置在线头像：后端用同一份 SVG 上传 OSS，保证与前端预览一致
     */
    public String applyPreset(Long userId, String presetId) {
        AvatarPresets.Preset preset = AvatarPresets.find(presetId);
        if (preset == null) {
            throw new BusinessException(400, "头像不存在：" + presetId);
        }
        byte[] bytes = AvatarPresets.toSvg(preset).getBytes(StandardCharsets.UTF_8);
        String key = PREFIX + "/u" + userId + "/preset-" + preset.id() + "-"
                + UUID.randomUUID().toString().substring(0, 8) + ".svg";
        return store(key, bytes, CONTENT_TYPES.get("svg"));
    }

    /** 读取 OSS 对象（供私有 bucket 下由后端代理输出头像） */
    public AvatarObject read(String key) {
        if (!isAvatarKey(key)) {
            throw new BusinessException(400, "非法的头像地址");
        }
        String ext = extractExt(key);
        String contentType = CONTENT_TYPES.getOrDefault(ext, "application/octet-stream");
        try (OSSObject obj = oss().getObject(avatarBucket(), key)) {
            byte[] bytes = obj.getObjectContent().readAllBytes();
            return new AvatarObject(bytes, contentType);
        } catch (com.aliyun.oss.OSSException e) {
            if ("NoSuchKey".equals(e.getErrorCode())) {
                throw new BusinessException(404, "头像不存在");
            }
            throw new BusinessException(500, "头像读取失败：" + e.getErrorCode());
        } catch (Exception e) {
            log.warn("头像读取失败：{} {}", key, e.getMessage());
            throw new BusinessException(500, "头像读取失败");
        }
    }

    /** 代理返回的头像内容 */
    public record AvatarObject(byte[] bytes, String contentType) {
    }

    /**
     * 删除旧头像（仅清理本服务写入的 avatars/ 对象），失败只告警不影响主流程
     */
    public void deleteQuietly(String url) {
        String key = objectKeyOf(url);
        if (key == null) {
            return;
        }
        try {
            oss().deleteObject(avatarBucket(), key);
        } catch (Exception e) {
            log.warn("删除旧头像失败：{} {}", key, e.getMessage());
        }
    }

    /* ------------------------------ 内部实现 ------------------------------ */

    /**
     * 上传对象并返回可长期访问的头像地址
     * <p>
     * access-mode=proxy（默认）：对象保持私有，返回后端代理地址
     * {@code /api/user/avatar/raw?key=...}，由后端带凭证从 OSS 取流输出；
     * access-mode=public：额外给对象打 public-read ACL，返回 OSS 永久直链。
     * 两种模式都不使用签名 URL（签名会过期，头像会失效）。
     * </p>
     */
    private String store(String key, byte[] bytes, String contentType) {
        ObjectMetadata metadata = new ObjectMetadata();
        metadata.setContentLength(bytes.length);
        if (contentType != null) {
            metadata.setContentType(contentType);
        }
        String bucket = avatarBucket();
        OSS oss = oss();
        oss.putObject(new PutObjectRequest(bucket, key, new ByteArrayInputStream(bytes), metadata));
        log.info("头像已上传 OSS：bucket={} key={}", bucket, key);

        if (!isPublicMode()) {
            return proxyUrl(key);
        }
        // bucket 已配置为公共读，验证直链确实可匿名读取；异常情况兜底走代理
        String url = publicUrl(key);
        if (publiclyReadable(url)) {
            return url;
        }
        log.warn("头像直链不可匿名读取（key={}），请检查 bucket 是否为公共读；改为后端代理访问", key);
        return proxyUrl(key);
    }

    /** 匿名 HEAD 探测直链是否可公开读取（单次、超时 4s，失败即走代理兜底） */
    private boolean publiclyReadable(String url) {
        java.net.HttpURLConnection conn = null;
        try {
            conn = (java.net.HttpURLConnection) new java.net.URL(url).openConnection();
            conn.setRequestMethod("HEAD");
            conn.setConnectTimeout(4000);
            conn.setReadTimeout(4000);
            return conn.getResponseCode() == java.net.HttpURLConnection.HTTP_OK;
        } catch (Exception e) {
            log.warn("探测头像直链失败：{} {}", url, e.getMessage());
            return false;
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
    }

    private boolean isPublicMode() {
        StorageProperties.Avatar avatar = properties.getAvatar();
        return avatar != null && "public".equalsIgnoreCase(avatar.getAccessMode());
    }

    /** 头像专用 bucket（公共读）；未单独配置时退回资料 bucket */
    private String avatarBucket() {
        StorageProperties.Oss oss = properties.getOss();
        return oss != null && !isBlank(oss.getAvatarBucketName())
                ? oss.getAvatarBucketName().trim()
                : oss.getBucketName();
    }

    /** 懒加载 OSS 客户端：头像功能独立于资料存储开关，但依赖 OSS 凭证 */
    private OSS oss() {
        if (client == null) {
            synchronized (this) {
                if (client == null) {
                    StorageProperties.Oss oss = properties.getOss();
                    java.util.List<String> missing = new java.util.ArrayList<>();
                    if (oss == null) {
                        missing.add("oss(整段)");
                    } else {
                        if (isBlank(oss.getEndpoint())) missing.add("endpoint");
                        if (isBlank(oss.getAccessKeyId())) missing.add("access-key-id");
                        if (isBlank(oss.getAccessKeySecret())) missing.add("access-key-secret");
                        if (isBlank(oss.getBucketName())) missing.add("bucket-name");
                    }
                    if (!missing.isEmpty()) {
                        throw new BusinessException(500,
                                "未配置阿里云 OSS 凭证，无法保存头像（缺失：" + String.join("、", missing) + "）");
                    }
                    client = new OSSClientBuilder()
                            .build(oss.getEndpoint(), oss.getAccessKeyId(), oss.getAccessKeySecret());
                }
            }
        }
        return client;
    }

    /** 永久访问地址：https://{avatarBucket}.{endpoint}/{key} */
    private String publicUrl(String key) {
        String endpoint = properties.getOss().getEndpoint().trim();
        endpoint = endpoint.replaceFirst("^https?://", "").replaceAll("/+$", "");
        return "https://" + avatarBucket() + "." + endpoint + "/" + key;
    }

    /** 私有 bucket 下的后端代理地址（相对路径，前端 <img> 直接可用） */
    private String proxyUrl(String key) {
        return PROXY_PATH + "?key=" + URLEncoder.encode(key, StandardCharsets.UTF_8);
    }

    /** 仅允许访问 avatars/ 前缀，且禁止路径穿越 */
    private static boolean isAvatarKey(String key) {
        return key != null && key.startsWith(PREFIX + "/") && !key.contains("..") && !key.startsWith("/");
    }

    /** 从直链或代理地址反解 objectKey，仅处理本服务生成的头像地址 */
    private String objectKeyOf(String url) {
        if (url == null || url.isBlank()) {
            return null;
        }
        if (url.startsWith(PROXY_PATH)) {
            int q = url.indexOf("key=");
            if (q < 0) {
                return null;
            }
            String key = URLDecoder.decode(url.substring(q + 4), StandardCharsets.UTF_8);
            return isAvatarKey(key) ? key : null;
        }
        int idx = url.indexOf("//");
        if (idx < 0) {
            return null;
        }
        String rest = url.substring(idx + 2);
        int slash = rest.indexOf('/');
        if (slash < 0) {
            return null;
        }
        String host = rest.substring(0, slash);
        String key = rest.substring(slash + 1);
        String expectedHost = avatarBucket() + "."
                + properties.getOss().getEndpoint().trim().replaceFirst("^https?://", "").replaceAll("/+$", "");
        if (!host.equals(expectedHost) || !key.startsWith(PREFIX + "/")) {
            return null;
        }
        return key;
    }

    private static String extractExt(String filename) {
        if (filename == null) {
            return "";
        }
        int idx = filename.lastIndexOf('.');
        return idx < 0 ? "" : filename.substring(idx + 1).toLowerCase(Locale.ROOT);
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
