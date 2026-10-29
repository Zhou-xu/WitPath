package com.witpath.vo;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 用户信息 VO（禁止包含密码哈希等敏感字段）
 */
@Data
public class UserVO implements Serializable {

    private Long id;

    private String username;

    private String nickname;

    private String avatarUrl;

    private String email;

    private String phone;

    /** 角色编码：1 普通用户 2 管理员 */
    private Integer role;

    private String roleName;

    /** 状态：1 正常 2 禁用 3 待激活 */
    private Integer status;

    private String studyStage;

    private String studyGoal;

    private String preferredTags;

    private LocalDateTime lastLoginAt;

    private LocalDateTime createdAt;
}
