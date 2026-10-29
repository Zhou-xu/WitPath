package com.witpath.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.witpath.dto.UserAiRuntime;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;

/**
 * 提交给 FastAPI /api/ai/chat 的请求体
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ChatRequest implements Serializable {

    private Long userId;

    private String nickname;

    private String message;

    /** 多轮历史（role/content），最多携带最近 N 轮 */
    private List<HistoryItem> history = List.of();

    /** 是否启用资料检索（RAG） */
    private Boolean useKnowledge = true;

    /** 用户个人模型配置（解密后的明文，供 AI 服务直接使用；未配置时为 null） */
    private UserAiRuntime userModelConfig;

    /** 用户的结构化学习上下文摘要 */
    private JsonNode context;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class HistoryItem implements Serializable {
        private String role;
        private String content;
    }
}
