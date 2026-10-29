package com.witpath.config;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;

import java.io.IOException;

/**
 * 长整型安全序列化器
 * <p>
 * 项目主键统一使用雪花算法生成（19 位 Long），超出 JS Number 的安全整数范围
 * （2^53-1 = 9007199254740991），直接以数字形式下发到浏览器会被四舍五入，
 * 导致前端回传的 ID 与库中真实 ID 不一致（典型表现：登录会话不存在、
 * 按ID更新/删除 404）。
 * </p>
 * <p>
 * 策略：仅当值超出安全整数范围时才转字符串输出；计数、时间戳、文件大小等
 * 小数值保持数字，前端图表与数值计算不受影响。雪花 ID 恒为 19 位（&gt; 2^53），
 * 因此 ID 字段的输出类型稳定为字符串。
 * </p>
 */
public class SafeLongSerializer extends JsonSerializer<Long> {

    /** JS Number 可精确表示的最大整数（2^53-1） */
    public static final long MAX_SAFE_INTEGER = 9007199254740991L;

    @Override
    public void serialize(Long value, JsonGenerator gen, SerializerProvider serializers) throws IOException {
        if (value == null) {
            gen.writeNull();
            return;
        }
        if (value > MAX_SAFE_INTEGER || value < -MAX_SAFE_INTEGER) {
            gen.writeString(Long.toString(value));
        } else {
            gen.writeNumber(value.longValue());
        }
    }
}
