package com.witpath.vo;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * AI 会话消息 VO
 */
@Data
public class ChatMessageVO implements Serializable {

    private Long id;

    private Long conversationId;

    /** user / assistant */
    private String role;

    private String content;

    /** 引用的资料片段 */
    private JsonNode referenced;

    private Integer tokensUsed;

    private LocalDateTime createdAt;
}
