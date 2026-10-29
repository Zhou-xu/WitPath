package com.witpath.enumeration;

import lombok.Getter;

/**
 * 资料处理状态（解析 / 向量化共用）
 */
@Getter
public enum ProcessStatus {

    INIT(0, "未处理"),
    PROCESSING(1, "处理中"),
    DONE(2, "已完成"),
    FAILED(3, "失败");

    private final Integer code;
    private final String desc;

    ProcessStatus(Integer code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    public static ProcessStatus of(Integer code) {
        if (code == null) {
            return INIT;
        }
        for (ProcessStatus status : values()) {
            if (status.code.equals(code)) {
                return status;
            }
        }
        return INIT;
    }

    public static String desc(Integer code) {
        return of(code).getDesc();
    }
}
