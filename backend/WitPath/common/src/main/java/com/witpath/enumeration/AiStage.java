package com.witpath.enumeration;

import lombok.Getter;

/**
 * LangGraph 工作流节点名称（与 FastAPI 侧保持一致）
 */
@Getter
public enum AiStage {

    QUEUED("queued", "已排队"),
    DATA_COLLECT("data_collect", "整理学习数据"),
    ANALYSIS("analysis", "分析学习态势"),
    PLANNING("planning", "生成学习计划"),
    RETRIEVAL("retrieval", "检索知识资料"),
    ADVICE("advice", "生成改进建议"),
    SUMMARY("summary", "整合结论");

    private final String key;
    private final String label;

    AiStage(String key, String label) {
        this.key = key;
        this.label = label;
    }

    /**
     * 该阶段完成时对应的总体进度百分比
     */
    public int progress() {
        return switch (this) {
            case QUEUED -> 0;
            case DATA_COLLECT -> 20;
            case ANALYSIS -> 40;
            case PLANNING -> 60;
            case RETRIEVAL -> 75;
            case ADVICE -> 90;
            case SUMMARY -> 100;
        };
    }

    public static AiStage of(String key) {
        for (AiStage stage : values()) {
            if (stage.key.equalsIgnoreCase(key)) {
                return stage;
            }
        }
        return null;
    }
}
