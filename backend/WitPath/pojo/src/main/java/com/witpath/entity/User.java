package com.witpath.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 用户实体类
 * <p>
 * 字段与 database/WitPath.sql 中 user 表严格一一对应。
 * 注意：密码列名为 password_hash（BCrypt 哈希），头像列名为 avatar_url。
 * </p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("user")
public class User implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String username;

    /** 数据库列名 password_hash */
    private String passwordHash;

    private String nickname;

    /** 数据库列名 avatar_url */
    private String avatarUrl;

    private String email;

    private String phone;

    /**
     * 角色：1 普通用户，2 管理员，见 com.witpath.enumeration.UserRole
     */
    private Integer role;

    /**
     * 账号状态：1 正常，2 禁用，3 待激活
     */
    private Integer status;

    /** 学习阶段：高中/大学/考研/职场等 */
    private String studyStage;

    /** 当前学习目标 */
    private String studyGoal;

    /** 学习偏好标签，逗号分隔 */
    private String preferredTags;

    private LocalDateTime lastLoginAt;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    /** 逻辑删除：0 未删除，1 已删除 */
    private Integer isDeleted;
}
