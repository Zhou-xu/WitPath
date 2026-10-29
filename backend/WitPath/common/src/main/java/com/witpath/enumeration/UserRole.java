package com.witpath.enumeration;

import lombok.Getter;

/**
 * 用户角色
 */
@Getter
public enum UserRole {

    USER(1, "普通用户"),
    ADMIN(2, "管理员");

    private final Integer code;
    private final String desc;

    UserRole(Integer code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    public static UserRole of(Integer code) {
        if (code == null) {
            return USER;
        }
        for (UserRole role : values()) {
            if (role.code.equals(code)) {
                return role;
            }
        }
        return USER;
    }
}
