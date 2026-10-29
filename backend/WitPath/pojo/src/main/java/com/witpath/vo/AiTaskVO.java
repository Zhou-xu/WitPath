package com.witpath.vo;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * AI 任务 VO
 */
@Data
public class AiTaskVO implements Serializable {

    private Long id;

    private String taskNo;

    private String type;

    private String title;

    private String question;

    private Integer status;

    private String statusText;

    private String stage;

    private String stageText;

    private String stageMessage;

    private Integer progress;

    private String summary;

    private String suggestion;

    /** AI 服务返回的完整结构化结果 */
    private JsonNode result;

    private String model;

    private Integer tokensUsed;

    private Integer latencyMs;

    private String errorMessage;

    private LocalDateTime startedAt;

    private LocalDateTime finishedAt;

    private LocalDateTime createdAt;
}
