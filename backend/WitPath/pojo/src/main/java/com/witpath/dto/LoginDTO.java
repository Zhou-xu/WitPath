package com.witpath.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 登录请求 DTO
 */
@Data
public class LoginDTO {

    /**
     * 登录初始化 ID（前端从 LoginInitId 接口获取）
     * <p>
     * 雪花 ID 为 19 位大整数，超出 JS Number 安全整数范围，前端只能以字符串承载，
     * 因此这里直接按字符串接收，由 Service 显式解析，不依赖 Jackson 的标量隐式转换。
     * </p>
     */
    private String loginInitId;

    /**
     * 用户名
     */
    @NotBlank(message = "用户名不能为空")
    private String username;

    /**
     * 密码
     */
    @NotBlank(message = "密码不能为空")
    private String password;

    /**
     * 图形验证码答案
     */
    @NotBlank(message = "验证码不能为空")
    private String captchaAnswer;
}
