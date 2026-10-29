package com.witpath.utils;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** AES-GCM 加解密：往返一致、IV 随机、篡改可检测 */
class ApiKeyCryptoTest {

    private static final byte[] KEY = ApiKeySecret.parse("00112233445566778899aabbccddeeff00112233445566778899aabbccddeeff");

    @Test
    void 加解密往返一致() throws Exception {
        String plain = "sk-test-key-1234567890";
        String cipher = ApiKeyCrypto.encrypt(plain, KEY);
        assertNotEquals(plain, cipher);
        assertEquals(plain, ApiKeyCrypto.decrypt(cipher, KEY));
    }

    @Test
    void 同一明文两次密文不同() throws Exception {
        String plain = "sk-same-input";
        String first = ApiKeyCrypto.encrypt(plain, KEY);
        String second = ApiKeyCrypto.encrypt(plain, KEY);
        // IV 随机，密文不应重复（重复意味着 IV 复用，GCM 下是严重安全问题）
        assertNotEquals(first, second);
        assertEquals(plain, ApiKeyCrypto.decrypt(first, KEY));
        assertEquals(plain, ApiKeyCrypto.decrypt(second, KEY));
    }

    @Test
    void 密文被篡改时解密失败() throws Exception {
        String cipher = ApiKeyCrypto.encrypt("sk-tamper-target", KEY);
        byte[] raw = Base64.getDecoder().decode(cipher);
        raw[raw.length - 1] ^= 0x01; // 改动最后一个字节（tag 或密文尾部）
        String broken = Base64.getEncoder().encodeToString(raw);
        assertThrows(Exception.class, () -> ApiKeyCrypto.decrypt(broken, KEY));
    }

    @Test
    void 错误密钥无法解密() throws Exception {
        String cipher = ApiKeyCrypto.encrypt("sk-wrong-key", KEY);
        byte[] otherKey = "ffffffffffffffffffffffffffffffff".getBytes(StandardCharsets.UTF_8);
        assertThrows(Exception.class, () -> ApiKeyCrypto.decrypt(cipher, otherKey));
    }

    @Test
    void 空值直接返回空() throws Exception {
        assertNull(ApiKeyCrypto.encrypt("", KEY));
        assertNull(ApiKeyCrypto.encrypt(null, KEY));
        assertNull(ApiKeyCrypto.decrypt("", KEY));
        assertNull(ApiKeyCrypto.decrypt(null, KEY));
    }
}
