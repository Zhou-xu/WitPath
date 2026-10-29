package com.witpath.dto;

import lombok.Data;

/**
 * 拉取模型列表入参：由后端代理访问用户提供的 OpenAI 兼容端点
 * <p>
 * 走后端代理的原因：① 避免浏览器直连第三方造成跨域失败；② API Key 不出现在前端。
 * </p>
 */
@Data
public class AiModelsDTO {

    /** 请求地址（可与表单中的 baseUrl 不同，用于临时探测） */
    private String baseUrl;

    /** API Key（明文，仅本次请求使用，不落库） */
    private String apiKey;
}
