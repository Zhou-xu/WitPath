package com.witpath.enumeration;

import lombok.Getter;

/**
 * AI 任务类型（与 FastAPI 侧 task_type 保持一致）
 */
@Getter
public enum AiTaskType {

    STUDY_ANALYSIS("STUDY_ANALYSIS", "学习态势分析"),
    STUDY_PLAN("STUDY_PLAN", "学习计划生成"),
    SUMMARY("SUMMARY", "阶段学习总结"),
    CHAT("CHAT", "AI 问答");

    private final String code;
    private final String desc;

    AiTaskType(String code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    public static AiTaskType of(String code) {
        for (AiTaskType type : values()) {
            if (type.code.equalsIgnoreCase(code)) {
                return type;
            }
        }
        return STUDY_ANALYSIS;
    }
}
