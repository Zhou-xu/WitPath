package com.witpath.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 用户注册请求 DTO
 */
@Data
public class RegisterDTO {

    /**
     * 前端从 /login/LoginInitId 获取的登录会话标识
     * <p>
     * 与 LoginDTO 保持一致，按字符串接收（雪花 ID 超出 JS 安全整数范围），
     * 由 Service 显式解析。
     * </p>
     */
    private String loginInitId;

    @NotBlank(message = "用户名不能为空")
    @Size(min = 3, max = 30, message = "用户名长度需为 3~30 位")
    @Pattern(regexp = "^[A-Za-z0-9_]+$", message = "用户名只能包含字母、数字和下划线")
    private String username;

    @NotBlank(message = "密码不能为空")
    @Size(min = 6, max = 32, message = "密码长度需为 6~32 位")
    private String password;

    @Size(max = 50, message = "昵称长度不能超过 50")
    private String nickname;

    @Pattern(regexp = "^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$|^$", message = "邮箱格式不正确")
    private String email;

    /** 学习阶段 */
    private String studyStage;

    /** 当前学习目标描述 */
    private String studyGoal;

    /** 学习偏好标签，逗号分隔 */
    private String preferredTags;

    @NotBlank(message = "验证码不能为空")
    private String captchaAnswer;

    /**
     * 前端获取的用户公网 IP（选填）
     * <p>
     * 前端通过公网 IP 探测服务获取后随注册请求带上；获取失败时留空，
     * 由后端从请求头（X-Forwarded-For / remoteAddr）兜底解析。
     * 仅允许 IP 合法字符，长度上限 45（IPv6 文本最大长度）。
     * </p>
     */
    @Size(max = 45, message = "IP 格式不正确")
    @Pattern(regexp = "^[0-9a-fA-F:.]*$", message = "IP 格式不正确")
    private String clientIp;
}
