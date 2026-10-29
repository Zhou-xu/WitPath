package com.witpath.server;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * 验证环境变量 APP_SECRET_KEY 是否对应用可见（排障用）。
 * <p>
 * 注意：不要把取值载体声明成 @Component —— 那样它会被主上下文扫描成 bean，
 * 一旦环境变量缺失（例如 CI / 本机未设置），整个 Spring 上下文都起不来。
 * 这里直接注入到测试类自身，并给占位符默认空值，缺失时只打印提示而不失败。
 * </p>
 */
@SpringBootTest
public class test {

    @Value("${APP_SECRET_KEY:}")
    private String secretKey;

    @Test
    public void test() {
        if (secretKey == null || secretKey.isBlank()) {
            System.out.println("APP_SECRET_KEY 未注入（当前进程看不到该系统环境变量，需重启终端或显式传入）");
        } else {
            System.out.println("APP_SECRET_KEY 已注入，长度=" + secretKey.length());
        }
    }
}
