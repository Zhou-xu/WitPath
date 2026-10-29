package com.witpath.service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.witpath.constant.Constants;
import com.witpath.properties.UserGeoLogProperties;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RedissonClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 用户归属地日志服务
 * <p>
 * 注册成功后由<b>独立线程池</b>异步查询 IP 归属地，并把带事件信息的完整记录
 * 追加写入 log/userlog.txt（经 logback 的 USER_LOG 定向 logger），
 * 用于事后统计用户地区分布与异常用户 IP。
 * </p>
 * <p>
 * 设计约束：任何查询/统计失败都不允许影响注册主流程——任务内部全量兜底，
 * 线程池打满时丢弃任务并告警，绝不阻塞或抛回调用方。
 * </p>
 */
@Slf4j
@Service
public class UserGeoLogService {

    /** 定向 logger：logback-spring.xml 中单独路由到 log/userlog.txt，不进控制台 */
    private static final Logger USER_LOG = LoggerFactory.getLogger("USER_LOG");

    private static final String EVENT_REGISTER = "USER_REGISTER";

    private final RedissonClient redissonClient;
    private final UserGeoLogProperties properties;

    /**
     * 归属地查询专用线程池：注册属于低频事件，核心 1 + 最大 2 + 1000 队列已足够；
     * 拒绝策略为丢弃并告警，避免高峰期反压到注册接口。
     */
    private final ThreadPoolExecutor executor;

    /** 归属地查询专用 RestClient（独立超时，避免复用 AI 服务长超时配置） */
    private final RestClient geoRestClient;

    public UserGeoLogService(RedissonClient redissonClient, UserGeoLogProperties properties) {
        this.redissonClient = redissonClient;
        this.properties = properties;

        AtomicInteger idx = new AtomicInteger(1);
        ThreadFactory threadFactory = r -> {
            Thread t = new Thread(r, "witpath-user-geo-" + idx.getAndIncrement());
            t.setDaemon(true);
            return t;
        };
        RejectedExecutionHandler rejectHandler = (r, e) ->
                log.warn("userlog 异步任务被丢弃（线程池与队列已满），当前队列长度={}", e.getQueue().size());
        this.executor = new ThreadPoolExecutor(1, 2, 60L, TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(1000), threadFactory, rejectHandler);

        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(
                HttpClient.newBuilder()
                        .connectTimeout(Duration.ofMillis(properties.getConnectTimeoutMs()))
                        .version(HttpClient.Version.HTTP_1_1)
                        .build());
        factory.setReadTimeout(Duration.ofMillis(properties.getReadTimeoutMs()));
        this.geoRestClient = RestClient.builder()
                .requestFactory(factory)
                .build();
    }

    /**
     * 记录一次注册事件（异步，立即返回）
     *
     * @param userId   新用户 ID
     * @param username 用户名
     * @param clientIp 前端获取的公网 IP，可能为空（由调用方兜底解析过）
     */
    public void recordRegister(Long userId, String username, String clientIp) {
        if (!properties.isEnabled()) {
            return;
        }
        String ip = clientIp == null ? "" : clientIp.trim();
        try {
            executor.execute(() -> doRecord(userId, username, ip));
        } catch (Exception e) {
            // 理论上拒绝策略已吞掉拒绝异常，这里再兜一层，保证注册不受影响
            log.warn("提交 userlog 异步任务失败：userId={} ip={}", userId, ip, e);
        }
    }

    /**
     * 实际查询与落盘逻辑，运行在专用线程池中，任何异常都在本方法内消化，
     * 且保证至少写出一行事件记录（极端异常时退化为最小字段行）
     */
    private void doRecord(Long userId, String username, String ip) {
        try {
            List<String> abnormalReasons = new ArrayList<>();

            if (ip.isEmpty()) {
                abnormalReasons.add("无法获取IP");
            } else if (isPrivateIp(ip)) {
                abnormalReasons.add("内网IP");
            }

            GeoResult geo = lookupGeo(ip);
            String country = "-";
            String province = "-";
            String city = "-";
            String isp = "-";
            if (geo == null) {
                if (!ip.isEmpty() && !isPrivateIp(ip)) {
                    abnormalReasons.add("归属地查询失败");
                }
            } else {
                country = geo.country();
                province = geo.province();
                city = geo.city();
                isp = geo.isp();
                if (isForeignCountry(country)) {
                    abnormalReasons.add("境外IP(" + country + ")");
                }
            }

            // 同 IP 注册次数统计（Redis 原子计数 + 窗口过期），达到阈值判定为批量注册
            long sameIpCount = countSameIpRegister(ip);
            if (sameIpCount >= properties.getSameIpThreshold()) {
                abnormalReasons.add("同IP重复注册(" + sameIpCount + "次/" + properties.getSameIpWindowHours() + "小时)");
            }

            USER_LOG.info("event=" + EVENT_REGISTER
                    + "|userId=" + userId
                    + "|username=" + username
                    + "|ip=" + (ip.isEmpty() ? "unknown" : ip)
                    + "|country=" + country
                    + "|province=" + province
                    + "|city=" + city
                    + "|isp=" + isp
                    + "|source=" + (geo == null ? "-" : geo.source())
                    + "|sameIpCount=" + sameIpCount
                    + "|abnormal=" + (!abnormalReasons.isEmpty())
                    + "|reason=" + (abnormalReasons.isEmpty() ? "-" : String.join(";", abnormalReasons)));
        } catch (Exception e) {
            // 兜底：宁可少字段也不能丢事件，方便事后补查
            log.warn("userlog 记录异常：userId={} username={} ip={}", userId, username, ip, e);
            USER_LOG.info("event=" + EVENT_REGISTER
                    + "|userId=" + userId
                    + "|username=" + username
                    + "|ip=" + (ip.isEmpty() ? "unknown" : ip)
                    + "|country=-|province=-|city=-|isp=-|source=-|sameIpCount=0"
                    + "|abnormal=true|reason=记录异常(" + e.getClass().getSimpleName() + ")");
        }
    }

    /**
     * 归属地查询：百度库优先（国内省市精度高），失败回退 ip-api（境外覆盖好）；
     * 全部失败返回 null，由判定逻辑标记异常
     */
    private GeoResult lookupGeo(String ip) {
        if (ip.isEmpty() || isPrivateIp(ip)) {
            return null;
        }
        GeoResult result = lookupByBaidu(ip);
        return result != null ? result : lookupByIpApi(ip);
    }

    /**
     * 首选源 百度 IP 库：免 key、中文直出，但只有 data[0].location 拼接串，需解析拆分
     */
    private GeoResult lookupByBaidu(String ip) {
        try {
            // URL 编码后替换占位符：IPv6 的冒号等字符直接拼 URL 会被 URI 解析拒绝
            String url = properties.getBaiduGeoApiUrl()
                    .replace("{ip}", URLEncoder.encode(ip, StandardCharsets.UTF_8));
            BaiduResponse resp = geoRestClient.get().uri(URI.create(url)).retrieve().body(BaiduResponse.class);
            if (resp == null || resp.data() == null || resp.data().isEmpty()) {
                log.debug("百度归属地返回空：ip={} status={}", ip, resp == null ? "null" : resp.status());
                return null;
            }
            String location = resp.data().get(0).location();
            if (location == null || location.isBlank() || location.contains("未知")) {
                log.debug("百度归属地返回无效 location：ip={} location={}", ip, location);
                return null;
            }
            return parseBaiduLocation(location.trim());
        } catch (Exception e) {
            log.warn("百度归属地查询异常：ip={} {}", ip, e.getMessage());
            return null;
        }
    }

    /**
     * 回退源 ip-api.com：结构化字段直接映射
     */
    private GeoResult lookupByIpApi(String ip) {
        try {
            String url = properties.getGeoApiUrl()
                    .replace("{ip}", URLEncoder.encode(ip, StandardCharsets.UTF_8));
            GeoInfo geo = geoRestClient.get().uri(URI.create(url)).retrieve().body(GeoInfo.class);
            if (geo == null || !"success".equalsIgnoreCase(geo.status())) {
                log.debug("ip-api 归属地返回失败：ip={} status={} message={}", ip,
                        geo == null ? "null" : geo.status(), geo == null ? "-" : geo.message());
                return null;
            }
            return new GeoResult(nz(geo.country()), nz(geo.regionName()), nz(geo.city()), nz(geo.isp()), "ip-api");
        } catch (Exception e) {
            log.warn("ip-api 归属地查询异常：ip={} {}", ip, e.getMessage());
            return null;
        }
    }

    /** 百度 location 末段可能出现的运营商关键词，命中才作为 isp 拆出 */
    private static final List<String> ISP_KEYWORDS =
            List.of("移动", "联通", "电信", "铁通", "广电", "教育网", "长城", "鹏博士", "有线");

    /**
     * 拆分百度 location 串（如“安徽省滁州市 移动”“北京市海淀区 联通”“美国”）：
     * 末段命中运营商关键词则拆出 isp，其余为地区串；
     * 国内地址按 省/自治区/特别行政区/直辖市 边界切出省市，境外地址首段为国家。
     * 识别不了的边界情况退化为“整串给 province / 首段给 country”，统计口径不受影响。
     */
    private GeoResult parseBaiduLocation(String location) {
        String[] parts = location.split("\\s+");
        String region = parts[0];
        String isp = "-";
        if (parts.length > 1 && ISP_KEYWORDS.stream().anyMatch(parts[parts.length - 1]::contains)) {
            isp = parts[parts.length - 1];
            region = String.join(" ", Arrays.copyOfRange(parts, 0, parts.length - 1));
        }

        if (isDomesticRegion(region)) {
            String province = region;
            String city = "-";
            if (region.contains("特别行政区")) {
                province = region;
            } else if (region.contains("自治区")) {
                int idx = region.indexOf("自治区") + "自治区".length();
                province = region.substring(0, idx);
                city = region.length() > idx ? region.substring(idx) : "-";
            } else if (region.contains("省")) {
                int idx = region.indexOf("省") + "省".length();
                province = region.substring(0, idx);
                city = region.length() > idx ? region.substring(idx) : "-";
            } else {
                // 直辖市：“北京市海淀区”→省=北京市、市=海淀区；“北京市”→市=-
                province = region.substring(0, 3);
                city = region.length() > 3 ? region.substring(3) : "-";
            }
            return new GeoResult("中国", province, city, isp, "baidu");
        }

        // 境外地址：首段为国家，其余拼为城市
        String[] segs = region.split("\\s+");
        String foreignCity = segs.length > 1
                ? String.join(" ", Arrays.copyOfRange(segs, 1, segs.length)) : "-";
        return new GeoResult(segs[0], "-", foreignCity, isp, "baidu");
    }

    /** 国内地区判断：含省/自治区/特别行政区字样、以“中国”开头或为四个直辖市 */
    private boolean isDomesticRegion(String region) {
        return region.contains("省") || region.contains("自治区") || region.contains("特别行政区")
                || region.startsWith("中国") || region.startsWith("北京") || region.startsWith("上海")
                || region.startsWith("天津") || region.startsWith("重庆");
    }

    /**
     * 同一 IP 在统计窗口内的注册次数；首日计数设置过期，避免 Redis 残留
     */
    private long countSameIpRegister(String ip) {
        if (ip.isEmpty()) {
            return 0;
        }
        try {
            var counter = redissonClient.getAtomicLong(
                    Constants.REDIS_REGISTER_IP_COUNT_KEY + ip);
            long count = counter.incrementAndGet();
            if (count == 1) {
                counter.expire(Duration.ofHours(properties.getSameIpWindowHours()));
            }
            return count;
        } catch (Exception e) {
            // Redis 不可用时不能影响日志落盘，按无统计处理
            log.warn("同 IP 注册计数失败：ip={} {}", ip, e.getMessage());
            return 0;
        }
    }

    /** 内网/保留地址判断（环回、私网、链路本地等），这类地址无法参与地区统计 */
    private boolean isPrivateIp(String ip) {
        String v = ip.toLowerCase();
        if ("::1".equals(v) || "0:0:0:0:0:0:0:1".equals(v) || v.isEmpty()) {
            return true;
        }
        if (v.contains(":")) {
            // IPv6：fc/fd 为 ULA 私网，fe80 为链路本地
            return v.startsWith("fc") || v.startsWith("fd") || v.startsWith("fe80");
        }
        if (v.startsWith("127.") || v.startsWith("10.") || v.startsWith("192.168.")
                || v.startsWith("169.254.") || v.startsWith("0.0.0.0")) {
            return true;
        }
        // 172.16.0.0 ~ 172.31.255.255 私网段；段号非法（如 "172..1"）按非私网处理，交给归属地查询报错
        if (v.startsWith("172.")) {
            try {
                int second = Integer.parseInt(v.split("\\.")[1]);
                return second >= 16 && second <= 31;
            } catch (RuntimeException e) {
                return false;
            }
        }
        return false;
    }

    /** 境外判断：兼容中英文供应商返回（“中国” / "China"） */
    private boolean isForeignCountry(String country) {
        if (country == null || country.equals("-")) {
            return false;
        }
        String c = country.trim();
        return !"中国".equals(c) && !"China".equalsIgnoreCase(c);
    }

    private String nz(String s) {
        return s == null || s.isBlank() ? "-" : s.trim();
    }

    /** 归属地解析结果：两个供应商统一归一化后的结构，source 标明实际命中的源 */
    private record GeoResult(String country, String province, String city, String isp, String source) {
    }

    /** ip-api.com 响应字段（fields 参数控制返回项，缺省项为 null） */
    @JsonIgnoreProperties(ignoreUnknown = true)
    private record GeoInfo(String status, String message, String country,
                           String regionName, String city, String isp, String query) {
    }

    /** 百度 IP 库响应：data[0].location 为“地区 运营商”拼接串 */
    @JsonIgnoreProperties(ignoreUnknown = true)
    private record BaiduResponse(String status, List<BaiduData> data) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record BaiduData(String location) {
    }

    @PreDestroy
    public void shutdown() {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
