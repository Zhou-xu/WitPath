package com.witpath.utils;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 用户 API Key 的加密主密钥（取自环境变量 APP_SECRET_KEY）
 * <p>
 * 两个关键修正（相对于初版）：
 * <ol>
 *   <li>占位符带默认空值：未配置该环境变量时应用仍能启动，
 *       报错推迟到真正读写用户配置时（而不是启动即崩溃，测试与本地都跑不起来）；</li>
 *   <li>解析兼容 hex / Base64 / 原始字符串：本机的 APP_SECRET_KEY 是 64 位 hex（32 字节 AES-256），
 *       纯 Base64 解码会得到 48 字节，运行时抛 "Invalid AES key length"，
 *       因此统一交给 {@link ApiKeySecret#parse(String)} 处理。</li>
 * </ol>
 * 解析延迟到首次取用，避免构造期异常把整个 Spring 上下文拖挂。
 * </p>
 */
@Slf4j
@Component
public class ApiKeyMasterKeyProvider {

    private final String rawKey;

    private volatile byte[] masterKey;

    public ApiKeyMasterKeyProvider(@Value("${APP_SECRET_KEY:}") String rawKey) {
        this.rawKey = rawKey;
    }

    /**
     * 取主密钥；缺失或长度非法时抛出带明确原因的异常（调用方按需降级或提示用户）
     */
    public byte[] getMasterKey() {
        if (masterKey == null) {
            synchronized (this) {
                if (masterKey == null) {
                    masterKey = ApiKeySecret.parse(rawKey);
                    log.info("API Key 加密主密钥已加载：{}", ApiKeySecret.fingerprint(masterKey));
                }
            }
        }
        return masterKey;
    }

    /** 密钥是否已可用（用于健康检查/降级判断，不抛异常） */
    public boolean available() {
        if (masterKey != null) {
            return true;
        }
        try {
            return ApiKeySecret.parse(rawKey).length > 0;
        } catch (Exception e) {
            return false;
        }
    }
}
