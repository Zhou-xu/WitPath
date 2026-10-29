package com.witpath.result;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ResultTest {

    @Test
    @DisplayName("success 默认状态码 200 且携带数据")
    void successDefaultsTo200() {
        Result<String> result = Result.success("hello");

        assertEquals(200, result.getCode());
        assertEquals("hello", result.getData());
        assertNull(result.getMsg());
    }

    @Test
    @DisplayName("success 支持自定义状态码")
    void successWithCustomCode() {
        Result<String> result = Result.success(201, "created");

        assertEquals(201, result.getCode());
        assertEquals("created", result.getData());
    }

    @Test
    @DisplayName("error 携带错误信息且不返回数据")
    void errorCarriesMessage() {
        Result<String> result = Result.error(400, "参数校验失败");

        assertEquals(400, result.getCode());
        assertEquals("参数校验失败", result.getMsg());
        assertNull(result.getData());
    }
}
