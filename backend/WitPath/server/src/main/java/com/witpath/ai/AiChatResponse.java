package com.witpath.ai;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * FastAPI AI 服务 /api/ai/chat 的响应体
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class AiChatResponse implements Serializable {

    private String reply;

    @JsonProperty("tokens_used")
    private Integer tokensUsed;

    private String model;

    /** 回复引用的资料片段 */
    private List<Reference> referenced = List.of();

    private String error;

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Reference implements Serializable {
        private String source;
        private String snippet;
        private Double score;
    }
}
