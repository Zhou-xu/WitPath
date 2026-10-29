package com.witpath.enumeration;

import lombok.Getter;

/**
 * 学习目标状态
 */
@Getter
public enum GoalStatus {

    IN_PROGRESS(1, "进行中"),
    COMPLETED(2, "已完成"),
    PAUSED(3, "已暂停"),
    ARCHIVED(4, "已归档");

    private final Integer code;
    private final String desc;

    GoalStatus(Integer code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    public static GoalStatus of(Integer code) {
        if (code == null) {
            return IN_PROGRESS;
        }
        for (GoalStatus status : values()) {
            if (status.code.equals(code)) {
                return status;
            }
        }
        return IN_PROGRESS;
    }

    public static String desc(Integer code) {
        return of(code).getDesc();
    }
}
