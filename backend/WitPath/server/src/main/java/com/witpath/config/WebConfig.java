package com.witpath.config;

import com.witpath.properties.StorageProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.io.File;

/**
 * Web 层配置：静态资源映射（本地学习资料下载）
 * 用于将本地学习资料目录映射到 /files/** 路径，实现文件下载功能。
 * 如果项目全部用 OSS 存储，这个配置实际上就不需要了。
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class WebConfig implements WebMvcConfigurer {

    private final StorageProperties storageProperties;

    @Value("${spring.servlet.multipart.max-file-size:20MB}")
    private String maxFileSize;

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        File dir = new File(storageProperties.getLocalDir());
        String location = "file:" + dir.getAbsolutePath().replace("\\", "/") + "/";
        registry.addResourceHandler("/files/**")
                .addResourceLocations(location)
                .setCachePeriod(3600);
        log.info("本地资料静态资源映射：/files/** -> {}", location);
    }
}
