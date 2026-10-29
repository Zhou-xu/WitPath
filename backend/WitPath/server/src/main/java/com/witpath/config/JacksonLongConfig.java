package com.witpath.config;

import com.fasterxml.jackson.databind.Module;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Jackson 全局配置：根治雪花 ID 在前端 JS 的精度丢失
 * <p>
 * 会话标识、目标/任务/记录/资料等主键均为 19 位雪花 ID，超出 JS 安全整数范围，
 * 统一在本模块注册 Long 的安全序列化器，业务代码无需逐字段加注解。
 * Spring Boot 会自动把容器中所有 Jackson Module 注册到 ObjectMapper。
 * </p>
 * <p>
 * 注意：本配置只作用于 Spring MVC 的响应序列化（即发给浏览器的 JSON）。
 * 后端调用 AI 服务的 RestClient 与 RabbitMQ 的 Jackson2JsonMessageConverter
 * 均使用各自独立的 ObjectMapper，服务间通信的数字契约不受影响。
 * </p>
 * <p>
 * 通过 {@code witpath.jackson.long-to-string} 切换策略：
 * <ul>
 *   <li>{@code auto}（默认）：仅超出 JS 安全整数范围的 Long 转字符串（雪花 ID 恒中招，
 *       计数/时间戳/文件大小保持数字，前端图表零影响）</li>
 *   <li>{@code all}：所有 Long 一律转字符串（更严格的一致性，但前端所有 Long
 *       字段都需按字符串处理）</li>
 * </ul>
 * </p>
 */
@Configuration
public class JacksonLongConfig {

    @Value("${witpath.jackson.long-to-string:auto}")
    private String longToStringMode;

    @Bean
    public Module witpathLongToStringModule() {
        SimpleModule module = new SimpleModule("witpath-long-to-string");
        boolean all = "all".equalsIgnoreCase(longToStringMode == null ? "" : longToStringMode.trim());
        if (all) {
            module.addSerializer(Long.class, ToStringSerializer.instance);
            module.addSerializer(Long.TYPE, ToStringSerializer.instance);
        } else {
            SafeLongSerializer serializer = new SafeLongSerializer();
            module.addSerializer(Long.class, serializer);
            module.addSerializer(Long.TYPE, serializer);
        }
        return module;
    }
}
