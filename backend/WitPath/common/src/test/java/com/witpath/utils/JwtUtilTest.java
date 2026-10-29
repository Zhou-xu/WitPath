package com.witpath.utils;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;

/**
 * JwtUtil 单元测试：覆盖签发、解析、过期与篡改场景。
 */
class JwtUtilTest {

    private static final String SECRET = "abcdefghijklmnopqrstuvwxyz123456abcdefghijklmnopqrstuvwxyz123456";

    private JwtUtil jwtUtil;

    @BeforeEach
    void setUp() {
        jwtUtil = new JwtUtil();
        ReflectionTestUtils.setField(jwtUtil, "secret", SECRET);
        ReflectionTestUtils.setField(jwtUtil, "expire", 604800000L);
    }

    @Test
    @DisplayName("签发的 token 可以解析回同一个 userId")
    void createThenParse() {
        String token = jwtUtil.createToken(10086L);
        assertNotNull(token);
        assertEquals(10086L, jwtUtil.getUserId(token));
        assertTrue(jwtUtil.validateToken(token));
    }

    @Test
    @DisplayName("不同用户签发的 token 互不相同")
    void tokenDiffersByUser() {
        assertNotEquals(jwtUtil.createToken(1L), jwtUtil.createToken(2L));
    }

    @Test
    @DisplayName("非法 / 被篡改的 token 校验不通过")
    void invalidTokenRejected() {
        assertFalse(jwtUtil.validateToken("not-a-jwt"));
        assertFalse(jwtUtil.validateToken(""));

        String token = jwtUtil.createToken(1L);
        String tampered = token.substring(0, token.length() - 2) + "xy";
        assertFalse(jwtUtil.validateToken(tampered));
    }

    @Test
    @DisplayName("过期 token 校验不通过")
    void expiredTokenRejected() {
        ReflectionTestUtils.setField(jwtUtil, "expire", -1000L);
        String expired = jwtUtil.createToken(1L);
        assertFalse(jwtUtil.validateToken(expired));
    }

    @Test
    @DisplayName("密钥不一致时 token 不可解析")
    void tokenSignedByOtherSecretRejected() {
        JwtUtil other = new JwtUtil();
        ReflectionTestUtils.setField(other, "secret", "another-secret-another-secret-another-secret-another-32");
        ReflectionTestUtils.setField(other, "expire", 604800000L);

        String token = jwtUtil.createToken(1L);
        assertFalse(other.validateToken(token));
    }
}
