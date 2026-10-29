package com.witpath.vo;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * AI 会话 VO
 */
@Data
public class ConversationVO implements Serializable {

    private Long id;

    private String title;

    private Integer messageCount;

    private LocalDateTime lastMessageAt;

    private LocalDateTime createdAt;
}
