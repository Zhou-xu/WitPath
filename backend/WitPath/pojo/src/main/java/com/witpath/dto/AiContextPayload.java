package com.witpath.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * 提交给 FastAPI AI 服务的学习上下文载荷
 * <p>
 * Spring Boot 负责数据组装与权限控制，AI 服务只做分析与生成。
 * </p>
 */
@Data
public class AiContextPayload implements Serializable {

    private Long userId;

    private String taskNo;

    private String taskType = "STUDY_ANALYSIS";

    private String question;

    /** Spring Boot 侧提供的进度回调地址，AI 服务在处理过程中回推阶段状态 */
    private String callbackUrl;

    /** 内部回调令牌 */
    private String callbackToken;

    private UserProfile profile = new UserProfile();

    private Summary summary = new Summary();

    private List<GoalItem> goals = List.of();

    private List<TaskItem> tasks = List.of();

    private List<RecordItem> records = List.of();

    private List<String> materialSnippets = List.of();

    /** 用户个人模型配置（解密后的明文，供 AI 服务直接使用；未配置时为 null） */
    private UserAiRuntime userModelConfig;

    @Data
    public static class UserProfile implements Serializable {
        private String nickname;
        private String studyStage;
        private String studyGoal;
        private List<String> preferredTags = List.of();
    }

    @Data
    public static class Summary implements Serializable {
        private Integer rangeDays = 14;
        private Integer totalMinutes = 0;
        private Integer recordCount = 0;
        private Integer streakDays = 0;
        private BigDecimal avgFocus = BigDecimal.ZERO;
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd")
        private LocalDate from;
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd")
        private LocalDate to;
    }

    @Data
    public static class GoalItem implements Serializable {
        private Long id;
        private String title;
        private String subject;
        private Integer status;
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd")
        private LocalDate startDate;
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd")
        private LocalDate endDate;
        private BigDecimal targetHours = BigDecimal.ZERO;
        private BigDecimal actualHours = BigDecimal.ZERO;
        private BigDecimal progress = BigDecimal.ZERO;
        private Integer taskTotal = 0;
        private Integer taskDone = 0;
    }

    @Data
    public static class TaskItem implements Serializable {
        private Long id;
        private Long goalId;
        private String title;
        private Integer status;
        private Integer priority;
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd")
        private LocalDate dueDate;
        private Integer estimatedMinutes = 0;
        private Boolean overdue = false;
    }

    @Data
    public static class RecordItem implements Serializable {
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd")
        private LocalDate studyDate;
        private String title;
        private String subject;
        private Integer durationMinutes = 0;
        private Integer focusScore = 0;
        private String outcome;
    }
}
