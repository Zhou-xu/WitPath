package com.witpath.utils;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Set;

/**
 * API Key 加密主密钥解析
 * <p>
 * 支持三种书写形式，按顺序识别，最终必须得到 16 / 24 / 32 字节（AES-128 / 192 / 256）：
 * <ol>
 *   <li>hex：32 / 48 / 64 个十六进制字符；</li>
 *   <li>Base64：解码后长度合规；</li>
 *   <li>原始字符串：UTF-8 字节长度恰为 16 / 24 / 32。</li>
 * </ol>
 * 注意顺序带来的歧义：一个只含 Base64 合法字符的 32 字符串会被按 Base64 解析（24 字节），
 * 若想表达 32 字节原始字符串，请使用 64 位 hex 或含非 Base64 字符（如 ! @ -）的写法。
 * 解析失败会抛出带明确原因的异常，便于启动时 fail-fast（而不是等到读写用户配置时才炸）。
 * </p>
 */
public final class ApiKeySecret {

    private static final Set<Integer> VALID_LENGTHS = Set.of(16, 24, 32);
    private static final String HEX = "^[0-9a-fA-F]+$";

    private ApiKeySecret() {
    }

    public static byte[] parse(String raw) {
        String value = raw == null ? "" : raw.trim();
        if (value.isEmpty()) {
            throw new IllegalStateException(
                    "未配置 API Key 加密主密钥：请设置环境变量 APP_SECRET_KEY（32/48/64 位 hex，或 16/24/32 字节密钥的 Base64）");
        }

        // 1) hex
        if (value.matches(HEX) && Set.of(32, 48, 64).contains(value.length())) {
            byte[] key = HexFormat.of().parseHex(value);
            requireValidLength(key.length, "hex");
            return key;
        }

        // 2) Base64
        try {
            byte[] key = Base64.getDecoder().decode(value);
            if (VALID_LENGTHS.contains(key.length)) {
                return key;
            }
        } catch (IllegalArgumentException ignored) {
            // 不是合法 Base64，继续按原始字符串处理
        }

        // 3) 原始字符串
        byte[] rawBytes = value.getBytes(StandardCharsets.UTF_8);
        requireValidLength(rawBytes.length, "原始字符串(UTF-8)");
        return rawBytes;
    }

    /** 密钥指纹：只取前 4 字节 hex，用于日志排障，不泄露密钥本身 */
    public static String fingerprint(byte[] key) {
        if (key == null || key.length < 4) {
            return "invalid";
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 4; i++) {
            sb.append(String.format("%02x", key[i]));
        }
        return sb + "...(" + key.length * 8 + "bit)";
    }

    private static void requireValidLength(int length, String form) {
        if (!VALID_LENGTHS.contains(length)) {
            throw new IllegalStateException(
                    "APP_SECRET_KEY 长度不合法（" + form + " 解析得到 " + length + " 字节），需要 16 / 24 / 32 字节");
        }
    }
}
