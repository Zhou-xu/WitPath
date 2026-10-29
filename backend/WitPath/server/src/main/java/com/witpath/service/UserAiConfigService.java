package com.witpath.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.witpath.constant.Constants;
import com.witpath.dto.AiModelsDTO;
import com.witpath.dto.UserAiConfigDTO;
import com.witpath.dto.UserAiRuntime;
import com.witpath.entity.UserApiKey;
import com.witpath.exception.BusinessException;
import com.witpath.mapper.UserApiKeyMapper;
import com.witpath.properties.SecurityProperties;
import com.witpath.utils.ApiKeyCrypto;
import com.witpath.utils.ApiKeyMasterKeyProvider;
import com.witpath.vo.ModelOptionVO;
import com.witpath.vo.UserAiConfigVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RBucket;
import org.redisson.api.RedissonClient;
import org.springframework.boot.http.client.ClientHttpRequestFactoryBuilder;
import org.springframework.boot.http.client.ClientHttpRequestFactorySettings;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * 用户个人大模型配置服务
 * <p>
 * 职责边界：
 * <ol>
 *   <li>API Key 的加解密（AES-GCM，主密钥来自环境变量 APP_SECRET_KEY），库内只有密文；</li>
 *   <li>解密结果缓存 5 分钟，避免每次 AI 调用都读库 + 解密；</li>
 *   <li>代理拉取用户端点上的模型列表（避免跨域与 Key 外泄）。</li>
 * </ol>
 * </p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserAiConfigService {

    /** 默认服务商标识 */
    private static final String DEFAULT_PROVIDER = "openai-compatible";

    /** 拉取模型列表的超时：连接 5s / 读取 12s，避免用户填错地址把线程挂住 */
    private static final Duration MODELS_CONNECT_TIMEOUT = Duration.ofSeconds(5);
    private static final Duration MODELS_READ_TIMEOUT = Duration.ofSeconds(12);

    /** 单次返回的模型数量上限，防止超大列表压垮下拉框 */
    private static final int MAX_MODELS = 200;

    private final UserApiKeyMapper userApiKeyMapper;
    private final SecurityProperties securityProperties;
    private final ApiKeyMasterKeyProvider masterKeyProvider;
    private final RedissonClient redissonClient;
    private final ObjectMapper objectMapper;

    /** 解密结果缓存时长，取 witpath.security.ai-config-cache-ttl-seconds（默认 300 秒） */
    private long ttlSeconds() {
        long ttl = securityProperties.getAiConfigCacheTtlSeconds();
        return ttl > 0 ? ttl : 300L;
    }

    /* ------------------------------------------------------------------ */
    /* 配置读写                                                            */
    /* ------------------------------------------------------------------ */

    public UserAiConfigVO get(Long userId) {
        UserAiConfigVO vo = new UserAiConfigVO();
        UserApiKey entity = selectActive(userId);
        if (entity == null) {
            return vo;
        }
        vo.setConfigured(true);
        vo.setProvider(entity.getProvider());
        vo.setBaseUrl(entity.getBaseUrl());
        vo.setModel(entity.getModel());
        vo.setApiKeyMasked(mask(decrypt(entity.getApiKeyEnc())));
        vo.setEmbeddingProvider(entity.getEmbeddingProvider());
        vo.setEmbeddingModel(entity.getEmbeddingModel());
        vo.setEmbeddingBaseUrl(entity.getEmbeddingBaseUrl());
        vo.setEmbeddingApiKeyMasked(mask(decrypt(entity.getEmbeddingApiKeyEnc())));
        vo.setEmbeddingOverridden(entity.getEmbeddingModel() != null && !entity.getEmbeddingModel().isBlank());
        vo.setUseDefaultApi(entity.getUseDefaultApi() == null ? false : entity.getUseDefaultApi() == 1);
        vo.setUpdatedAt(entity.getUpdatedAt());
        return vo;
    }

    /**
     * 切换回系统默认模型：配置行保留（可随时改回），仅标记为不生效。
     * 从未配置过个人模型的用户调用本方法不会报错，只是没有实际变化。
     */
    public UserAiConfigVO useDefault(Long userId) {
        UserApiKey entity = selectActive(userId);
        if (entity == null) {
            // 本来就没有个人配置，天然就是系统默认
            return get(userId);
        }
        entity.setUseDefaultApi(1);
        userApiKeyMapper.updateById(entity);
        warmCache(userId);
        log.info("用户已切换回系统默认模型：userId={}", userId);
        return get(userId);
    }

    public UserAiConfigVO save(Long userId, UserAiConfigDTO dto) {
        if (isBlank(dto.getBaseUrl())) {
            throw new BusinessException(400, "请填写请求地址");
        }
        if (isBlank(dto.getModel())) {
            throw new BusinessException(400, "请先点击「获取模型」并选择要使用的模型");
        }

        String provider = isBlank(dto.getProvider()) ? DEFAULT_PROVIDER : dto.getProvider().trim();
        UserApiKey entity = selectActive(userId);
        // 首次配置必须给 Key；已有配置时留空表示"不修改"，避免只想换模型却被迫重新粘贴 Key
        if (isBlank(dto.getApiKey()) && entity == null) {
            throw new BusinessException(400, "请填写 API Key");
        }
        boolean insert = entity == null;
        UserApiKey target = insert ? new UserApiKey() : entity;

        target.setUserId(userId);
        target.setProvider(provider);
        if (!isBlank(dto.getApiKey())) {
            target.setApiKeyEnc(encrypt(dto.getApiKey().trim()));
        }
        target.setBaseUrl(dto.getBaseUrl().trim());
        target.setModel(dto.getModel().trim());
        // 高级配置：留空表示不覆盖系统默认
        target.setEmbeddingProvider(blankToNull(dto.getEmbeddingProvider()));
        target.setEmbeddingModel(blankToNull(dto.getEmbeddingModel()));
        target.setEmbeddingBaseUrl(blankToNull(dto.getEmbeddingBaseUrl()));
        if (!isBlank(dto.getEmbeddingApiKey())) {
            target.setEmbeddingApiKeyEnc(encrypt(dto.getEmbeddingApiKey().trim()));
        } else if (isBlank(dto.getEmbeddingModel())) {
            // 未填写向量模型：清空可能残留的旧向量配置，避免"半套配置"继续生效
            target.setEmbeddingApiKeyEnc(null);
        }
        target.setStatus(1);
        // 保存个人配置即视为启用；显式传 true 表示"保存但仍使用系统默认"
        target.setUseDefaultApi(Boolean.TRUE.equals(dto.getUseDefaultApi()) ? 1 : 0);

        if (insert) {
            userApiKeyMapper.insert(target);
        } else {
            userApiKeyMapper.updateById(target);
        }
        warmCache(userId);
        log.info("用户个人模型配置已保存：userId={} provider={} model={}", userId, provider, target.getModel());
        return get(userId);
    }

    public void delete(Long userId) {
        UserApiKey entity = selectActive(userId);
        if (entity == null) {
            return;
        }
        // 物理删除：逻辑删除残留行会与 uk_user_provider 冲突，导致用户无法重新配置
        userApiKeyMapper.deleteById(entity.getId());
        evictCache(userId);
        log.info("用户个人模型配置已删除：userId={}", userId);
    }

    /* ------------------------------------------------------------------ */
    /* 模型列表：后端代理访问用户的 OpenAI 兼容端点                          */
    /* ------------------------------------------------------------------ */

    public List<ModelOptionVO> fetchModels(Long userId, AiModelsDTO dto) {
        if (isBlank(dto.getBaseUrl())) {
            throw new BusinessException(400, "请先填写请求地址");
        }
        if (isBlank(dto.getApiKey())) {
            throw new BusinessException(400, "请先填写 API Key");
        }

        String base = dto.getBaseUrl().trim();
        String apiKey = dto.getApiKey().trim();
        List<String> candidates = candidateUrls(base);

        RestClient client = modelsClient();
        RestClientResponseException lastError = null;
        for (String url : candidates) {
            try {
                String body = client.get()
                        .uri(url)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                        .accept(MediaType.APPLICATION_JSON)
                        .retrieve()
                        .body(String.class);
                List<ModelOptionVO> models = parseModels(body);
                if (!models.isEmpty()) {
                    log.info("拉取模型列表成功：userId={} url={} 数量={}", userId, url, models.size());
                    return models;
                }
            } catch (RestClientResponseException e) {
                lastError = e;
                log.warn("拉取模型列表失败：url={} status={}", url, e.getStatusCode().value());
            } catch (Exception e) {
                log.warn("拉取模型列表异常：url={} msg={}", url, e.getMessage());
            }
        }

        if (lastError != null && lastError.getStatusCode().value() == 401) {
            throw new BusinessException(400, "该地址拒绝访问（401）：请检查 API Key 是否正确、是否有该模型的权限");
        }
        if (lastError != null && lastError.getStatusCode().value() == 404) {
            throw new BusinessException(400, "该地址未找到模型列表（404）：请确认请求地址为 OpenAI 兼容端点");
        }
        throw new BusinessException(400, "获取模型列表失败：请检查请求地址与 API Key 是否正确");
    }

    /**
     * OpenAI 兼容端点的 /models 地址候选：
     * 用户可能填 https://xxx/v1，也可能填 https://xxx，两种都尝试一次。
     */
    private List<String> candidateUrls(String base) {
        String normalized = base.replaceAll("/+$", "");
        List<String> urls = new ArrayList<>();
        urls.add(normalized + "/models");
        if (!normalized.endsWith("/v1")) {
            urls.add(normalized + "/v1/models");
        }
        return urls;
    }

    private RestClient modelsClient() {
        ClientHttpRequestFactorySettings settings = ClientHttpRequestFactorySettings.defaults()
                .withConnectTimeout(MODELS_CONNECT_TIMEOUT)
                .withReadTimeout(MODELS_READ_TIMEOUT);
        // 同 RestClientConfig：强制 HTTP/1.1，避免 JDK HttpClient 的 h2c 升级头导致
        // 用户自建端点（多为 uvicorn / vLLM / Ollama）返回 400
        ClientHttpRequestFactory factory = ClientHttpRequestFactoryBuilder.jdk()
                .withHttpClientCustomizer(builder -> builder.version(HttpClient.Version.HTTP_1_1))
                .build(settings);
        return RestClient.builder().requestFactory(factory).build();
    }

    /** 解析 {"data":[{"id":"xxx"}]}，兼容少数服务商返回 {data:[{"name":..}]} 或直接数组 */
    private List<ModelOptionVO> parseModels(String body) {
        if (body == null || body.isBlank()) {
            return List.of();
        }
        try {
            JsonNode root = objectMapper.readTree(body);
            JsonNode data = root.isArray() ? root : root.get("data");
            if (data == null || !data.isArray()) {
                return List.of();
            }
            Set<String> seen = new LinkedHashSet<>();
            List<ModelOptionVO> models = new ArrayList<>();
            for (JsonNode item : data) {
                String id = firstNonBlank(item, "id", "name", "model");
                if (id == null || !seen.add(id)) {
                    continue;
                }
                String name = firstNonBlank(item, "display_name", "displayName", "owned_by", "id", "name");
                models.add(new ModelOptionVO(id, name == null ? id : name));
            }
            models.sort(Comparator.comparing(ModelOptionVO::getId, String.CASE_INSENSITIVE_ORDER));
            return models.size() > MAX_MODELS ? new ArrayList<>(models.subList(0, MAX_MODELS)) : models;
        } catch (Exception e) {
            log.warn("解析模型列表失败：{}", e.getMessage());
            return List.of();
        }
    }

    private static String firstNonBlank(JsonNode node, String... fields) {
        for (String field : fields) {
            JsonNode value = node.get(field);
            if (value != null && !value.isNull() && !value.asText().isBlank()) {
                return value.asText();
            }
        }
        return null;
    }

    /* ------------------------------------------------------------------ */
    /* AI 调用侧：取解密后的运行时配置（带 5 分钟缓存）                      */
    /* ------------------------------------------------------------------ */

    /**
     * 交给 AI 网关使用的明文配置。
     * 优先读 Redis 缓存（解密结果，TTL 5 分钟），未命中才读库 + 解密并回填，
     * 避免每次 AI 调用都做一次解密（正是需求里要避免的开销）。
     */
    public UserAiRuntime resolve(Long userId) {
        if (userId == null) {
            return UserAiRuntime.empty();
        }
        RBucket<String> cache = redissonClient.getBucket(Constants.REDIS_AI_CONFIG_KEY + userId);
        String cached = cache.get();
        if (cached != null && !cached.isBlank()) {
            try {
                return objectMapper.readValue(cached, UserAiRuntime.class);
            } catch (Exception e) {
                log.warn("用户模型配置缓存解析失败，回退读库：userId={} msg={}", userId, e.getMessage());
            }
        }

        UserAiRuntime runtime = loadAndDecrypt(userId);
        try {
            cache.set(objectMapper.writeValueAsString(runtime), ttlSeconds(), TimeUnit.SECONDS);
        } catch (Exception e) {
            log.warn("写入用户模型配置缓存失败：userId={} msg={}", userId, e.getMessage());
        }
        return runtime;
    }

    private UserAiRuntime loadAndDecrypt(Long userId) {
        UserApiKey entity = selectActive(userId);
        if (entity == null) {
            return UserAiRuntime.empty();
        }
        // 用户选择了「使用默认 API」：配置保留在库里，但不下发给 AI 服务
        if (entity.getUseDefaultApi() != null && entity.getUseDefaultApi() == 1) {
            return UserAiRuntime.empty();
        }
        return UserAiRuntime.builder()
                .llmApiKey(decrypt(entity.getApiKeyEnc()))
                .llmBaseUrl(entity.getBaseUrl())
                .llmModel(entity.getModel())
                .llmProvider(entity.getProvider())
                .embeddingProvider(entity.getEmbeddingProvider())
                .embeddingModel(entity.getEmbeddingModel())
                .embeddingApiKey(decrypt(entity.getEmbeddingApiKeyEnc()))
                .embeddingBaseUrl(entity.getEmbeddingBaseUrl())
                .build();
    }

    private void evictCache(Long userId) {
        try {
            redissonClient.getBucket(Constants.REDIS_AI_CONFIG_KEY + userId).delete();
        } catch (Exception e) {
            log.warn("清理用户模型配置缓存失败：userId={} msg={}", userId, e.getMessage());
        }
    }

    /**
     * 配置变更后立即重建缓存（清旧值 + 解密一次写入），
     * 让紧随其后的 AI 调用直接命中缓存，不必再读库解密。
     * 切回系统默认时缓存的是空配置，同样避免每次都查库。
     */
    private void warmCache(Long userId) {
        evictCache(userId);
        try {
            UserAiRuntime runtime = loadAndDecrypt(userId);
            redissonClient
                    .getBucket(Constants.REDIS_AI_CONFIG_KEY + userId)
                    .set(objectMapper.writeValueAsString(runtime), ttlSeconds(), TimeUnit.SECONDS);
        } catch (Exception e) {
            log.warn("预热用户模型配置缓存失败：userId={} msg={}", userId, e.getMessage());
        }
    }

    private UserApiKey selectActive(Long userId) {
        return userApiKeyMapper.selectOne(new LambdaQueryWrapper<UserApiKey>()
                .eq(UserApiKey::getUserId, userId)
                .eq(UserApiKey::getIsDeleted, 0)
                .eq(UserApiKey::getStatus, 1)
                .orderByDesc(UserApiKey::getUpdatedAt)
                .last("LIMIT 1"));
    }

    /* ------------------------------------------------------------------ */
    /* 加解密                                                              */
    /* ------------------------------------------------------------------ */

    private String encrypt(String plain) {
        try {
            return ApiKeyCrypto.encrypt(plain, key());
        } catch (Exception e) {
            log.error("API Key 加密失败：{}", e.getMessage());
            throw new BusinessException(500, "配置加密失败，请联系管理员检查 APP_SECRET_KEY");
        }
    }

    private String decrypt(String cipher) {
        if (cipher == null || cipher.isBlank()) {
            return null;
        }
        try {
            return ApiKeyCrypto.decrypt(cipher, key());
        } catch (Exception e) {
            log.error("API Key 解密失败（密钥可能已轮换）：{}", e.getMessage());
            throw new BusinessException(500, "已保存的配置无法解密，请重新配置（若更换过 APP_SECRET_KEY 需重新保存）");
        }
    }

    /** 主密钥：统一取自 ApiKeyMasterKeyProvider（环境变量 APP_SECRET_KEY） */
    private byte[] key() {
        return masterKeyProvider.getMasterKey();
    }

    /** 掩码：保留前 6 后 4，中间统一 8 个 *，不泄露长度之外的信息 */
    private String mask(String plain) {
        if (plain == null || plain.isBlank()) {
            return null;
        }
        int len = plain.length();
        if (len <= 10) {
            return "****";
        }
        return plain.substring(0, 6) + "********" + plain.substring(len - 4);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

}
