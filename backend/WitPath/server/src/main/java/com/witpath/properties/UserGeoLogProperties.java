package com.witpath.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 用户归属地日志（userlog）配置
 * <p>
 * 注册成功后由独立线程池异步查询 IP 归属地并追加写入 log/userlog.txt，
 * 用于统计用户地区分布与异常用户 IP。
 * </p>
 */
@Data
@Component
@ConfigurationProperties(prefix = "witpath.user-geo-log")
public class UserGeoLogProperties {

    /** 功能总开关：false 时不创建异步任务、不写 userlog */
    private boolean enabled = true;

    /**
     * IP 归属地查询接口模板（回退源），{ip} 为占位符。
     * 百度首选源失败时使用，境外 IP 覆盖较好；换供应商时保持返回字段兼容：
     * status / country / regionName / city / isp
     */
    private String geoApiUrl = "http://ip-api.com/json/{ip}?lang=zh-CN&fields=status,message,country,regionName,city,isp,query";

    /**
     * 百度 IP 库查询地址模板（首选源，国内省市级精度高），{ip} 为占位符。
     * 返回 data[0].location 形如“安徽省滁州市 移动”，由服务解析拆分省市与运营商。
     */
    private String baiduGeoApiUrl = "https://opendata.baidu.com/api.php?query={ip}&co=&resource_id=6006&oe=utf8";

    /** 归属地查询连接超时（毫秒） */
    private int connectTimeoutMs = 3_000;

    /** 归属地查询读取超时（毫秒） */
    private int readTimeoutMs = 5_000;

    /** 同一 IP 在统计窗口内的注册次数达到该值即判定为异常（同 IP 批量注册） */
    private int sameIpThreshold = 3;

    /** 同 IP 注册次数统计窗口（小时），对应 Redis Key 的过期时间 */
    private int sameIpWindowHours = 24;
}
