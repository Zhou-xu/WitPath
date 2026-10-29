package com.witpath.enumeration;

import lombok.Getter;

/**
 * 学习任务状态
 */
@Getter
public enum TaskStatus {

    TODO(1, "待办"),
    DOING(2, "进行中"),
    DONE(3, "已完成"),
    DELAYED(4, "已延期");

    private final Integer code;
    private final String desc;

    TaskStatus(Integer code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    public static TaskStatus of(Integer code) {
        if (code == null) {
            return TODO;
        }
        for (TaskStatus status : values()) {
            if (status.code.equals(code)) {
                return status;
            }
        }
        return TODO;
    }

    public static String desc(Integer code) {
        return of(code).getDesc();
    }
}
