package com.witpath.config;

import com.witpath.properties.AiServiceProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.http.client.ClientHttpRequestFactoryBuilder;
import org.springframework.boot.http.client.ClientHttpRequestFactorySettings;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Duration;

/**
 * HTTP 客户端配置：Spring Boot 调用 FastAPI AI 服务
 */
@Slf4j
@Configuration
public class RestClientConfig {

    @Bean
    public RestClient aiRestClient(AiServiceProperties properties) {

        ClientHttpRequestFactorySettings settings = ClientHttpRequestFactorySettings.defaults()
                .withConnectTimeout(Duration.ofMillis(Math.max(1000L, properties.getHealthTimeoutMs())))
                .withReadTimeout(Duration.ofMillis(Math.max(1000L, properties.getTimeoutMs())));
        // 必须强制 HTTP/1.1：JDK HttpClient 默认按 HTTP/2 建连，明文下会先发 h2c 升级头
        // （Connection: Upgrade, HTTP2-Settings / Upgrade: h2c）。AI 服务是 uvicorn + h11，
        // 只认 HTTP/1.1，收到升级头会直接判定报文非法并返回 400 "Invalid HTTP request received."
        ClientHttpRequestFactory factory = ClientHttpRequestFactoryBuilder.jdk()
                .withHttpClientCustomizer(builder -> builder.version(HttpClient.Version.HTTP_1_1))
                .build(settings);

        return RestClient.builder()
                .baseUrl(properties.getBaseUrl())
                .requestFactory(factory)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                .build();
    }
}
