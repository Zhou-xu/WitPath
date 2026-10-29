package com.witpath.server;

import com.witpath.properties.AiServiceProperties;
import org.junit.jupiter.api.Test;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class ServerApplicationTests {

    @Autowired
    private RedissonClient redissonClient;

    @Autowired
    private AiServiceProperties aiServiceProperties;
    @Test
    void contextLoads() {
        System.out.println(aiServiceProperties.toString());


    }

}
