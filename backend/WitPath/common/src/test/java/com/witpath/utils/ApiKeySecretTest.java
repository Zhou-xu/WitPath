package com.witpath.utils;

import org.junit.jupiter.api.Test;

import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 主密钥解析规则：hex / Base64 / 原始字符串，以及缺失与非法长度的 fail-fast */
class ApiKeySecretTest {

    private static final String HEX_32 = "00112233445566778899aabbccddeeff"; // 16 字节
    private static final String HEX_64 = HEX_32 + HEX_32; // 32 字节

    @Test
    void 解析十六进制密钥() {
        assertEquals(16, ApiKeySecret.parse(HEX_32).length);
        assertEquals(32, ApiKeySecret.parse(HEX_64).length);
    }

    @Test
    void 解析Base64密钥() {
        byte[] key = ApiKeySecret.parse(HEX_32).clone();
        String base64 = Base64.getEncoder().encodeToString(new byte[32]);
        assertEquals(32, ApiKeySecret.parse(base64).length);
        assertEquals(16, key.length);
    }

    @Test
    void 解析原始字符串密钥() {
        // 含非 Base64 字符（!）时只能按 UTF-8 原始字节处理，长度需恰为 16 / 24 / 32
        String raw16 = "a!b!c!d!e!f!g!h!";
        assertEquals(16, ApiKeySecret.parse(raw16).length);
        assertEquals(32, ApiKeySecret.parse(raw16 + raw16).length);
    }

    @Test
    void 十六进制优先于原始字符串() {
        // 32 个 hex 字符 = 16 字节，而不是 32 字节的原始字符串
        assertEquals(16, ApiKeySecret.parse("1234567890abcdef1234567890abcdef").length);
    }

    @Test
    void 缺失密钥时快速失败() {
        assertThrows(IllegalStateException.class, () -> ApiKeySecret.parse(""));
        assertThrows(IllegalStateException.class, () -> ApiKeySecret.parse(null));
        assertThrows(IllegalStateException.class, () -> ApiKeySecret.parse("   "));
    }

    @Test
    void 非法长度时快速失败() {
        // 15 字节，既不是 16/24/32，也不是合法 hex 长度
        assertThrows(IllegalStateException.class, () -> ApiKeySecret.parse("1234567890abcde"));
        // 30 个 hex 字符：不是 32/48/64，也不构成合法 AES 长度
        assertThrows(IllegalStateException.class, () -> ApiKeySecret.parse("aabbccddeeaabbccddeeaabbccddee"));
    }

    @Test
    void 指纹不泄露完整密钥() {
        String fp = ApiKeySecret.fingerprint(ApiKeySecret.parse(HEX_64));
        assertTrue(fp.startsWith("00112233"), "指纹应取前 4 字节：" + fp);
        assertTrue(fp.contains("256bit"), fp);
        assertTrue(fp.length() < 24, "指纹必须远短于密钥本身");
    }
}
