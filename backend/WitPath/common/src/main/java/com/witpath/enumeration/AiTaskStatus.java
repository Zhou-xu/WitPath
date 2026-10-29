package com.witpath.enumeration;


import lombok.Getter;

/**
 * AI 任务状态
 */
@Getter
public enum AiTaskStatus {

    QUEUED(0, "排队中"),
    RUNNING(1, "执行中"),
    SUCCEEDED(2, "已完成"),
    FAILED(3, "失败"),
    CANCELED(4, "已取消");

    private final Integer code;
    private final String desc;

    AiTaskStatus(Integer code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    public static AiTaskStatus of(Integer code) {
        if (code == null) {
            return QUEUED;
        }
        for (AiTaskStatus status : values()) {
            if (status.code.equals(code)) {
                return status;
            }
        }
        return QUEUED;
    }

    public static String desc(Integer code) {
        return of(code).getDesc();
    }

    /** 是否为终态 */
    public static boolean isFinished(Integer code) {
        return of(code) == SUCCEEDED || of(code) == FAILED || of(code) == CANCELED;
    }
}
