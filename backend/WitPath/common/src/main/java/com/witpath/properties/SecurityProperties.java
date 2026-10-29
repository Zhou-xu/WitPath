package com.witpath.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 安全相关配置
 * <p>
 * 用户个人大模型 API Key 的加密主密钥取自环境变量 APP_SECRET_KEY，
 * 支持三种书写形式（按顺序识别）：
 * <ol>
 *   <li>十六进制：32 / 48 / 64 个 hex 字符 → AES-128 / 192 / 256；</li>
 *   <li>Base64：解码后长度为 16 / 24 / 32 字节；</li>
 *   <li>原始字符串：UTF-8 字节长度恰为 16 / 24 / 32。</li>
 * </ol>
 * 解析与长度校验见 com.witpath.utils.ApiKeySecret。
 * </p>
 */
@Data
@Component
@ConfigurationProperties(prefix = "witpath.security")
public class SecurityProperties {

    /**
     * 用户大模型配置解密结果的缓存时长（秒），默认 300 秒 = 5 分钟
     * <p>
     * 缓存的是解密后的明文配置，用于避免每次 AI 调用都读库 + 做一次 AES-GCM 解密；
     * 保存 / 切回系统默认时会立即重建缓存，不会读到旧值。
     * </p>
     */
    private long aiConfigCacheTtlSeconds = 300L;
}
