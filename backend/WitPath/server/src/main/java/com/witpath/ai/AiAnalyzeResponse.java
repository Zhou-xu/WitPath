package com.witpath.ai;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * FastAPI AI 服务 /api/ai/analyze 的响应体
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)//序列化有未知属性时，忽略它
public class AiAnalyzeResponse implements Serializable {

    @JsonProperty("task_no")
    private String taskNo;

    private String type;

    private String status;

    private String model;

    @JsonProperty("tokens_used")
    private Integer tokensUsed;

    @JsonProperty("latency_ms")
    private Integer latencyMs;

    private String summary;

    private String suggestion;

    private List<String> risks = List.of();

    private List<PlanItem> plan = List.of();

    private List<StageItem> stages = List.of();

    private String error;

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class PlanItem implements Serializable {
        private String title;
        private String detail;
        @JsonProperty("due_date")
        private String dueDate;
        @JsonProperty("estimated_minutes")
        private Integer estimatedMinutes;
        private String priority;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class StageItem implements Serializable {
        private String stage;
        private String message;
        private Integer progress;
    }
}
