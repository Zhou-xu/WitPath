package com.witpath.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import com.witpath.utils.SnowflakeIdGenerator;

@Configuration
public class LoginIdConfig {
    @Bean
    public SnowflakeIdGenerator snowflakeIdGenerator() {
        return new SnowflakeIdGenerator(1, 1);
    }

}
