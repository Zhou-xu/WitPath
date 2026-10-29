package com.witpath.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 登录成功返回 VO
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class LoginVO {

    /**
     * JWT Token
     */
    private String token;

    private Long userId;

    private String username;

    /**
     * 用户昵称
     */
    private String nickname;

    /**
     * 用户头像
     */
    private String avatar;

    /**
     * 角色：1 普通用户 2 管理员
     */
    private Integer role;

    /**
     * Token 有效期（毫秒）
     */
    private Long expire;

    public LoginVO(String token, String nickname, String avatar) {
        this.token = token;
        this.nickname = nickname;
        this.avatar = avatar;
    }
}
