package com.witpath.enumeration;

import lombok.Getter;

/**
 * 提醒状态
 */
@Getter
public enum ReminderStatus {

    UNREAD(0, "未读"),
    READ(1, "已读"),
    HANDLED(2, "已处理");

    private final Integer code;
    private final String desc;

    ReminderStatus(Integer code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    public static ReminderStatus of(Integer code) {
        if (code == null) {
            return UNREAD;
        }
        for (ReminderStatus status : values()) {
            if (status.code.equals(code)) {
                return status;
            }
        }
        return UNREAD;
    }

    public static String desc(Integer code) {
        return of(code).getDesc();
    }
}
