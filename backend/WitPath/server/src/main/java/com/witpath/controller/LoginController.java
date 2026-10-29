package com.witpath.controller;

import com.witpath.dto.LoginDTO;
import com.witpath.result.Result;
import com.witpath.service.AuthService;
import com.witpath.vo.CaptchaVO;
import com.witpath.vo.LoginVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

/**
 * 登录控制器（三步式会话保持原有接口契约不变）
 */
@Slf4j
@RestController
@RequestMapping("/login")
@RequiredArgsConstructor
public class LoginController {

    private final AuthService authService;

    /**
     * 第一步：前端获取登录会话标识
     * <p>
     * 会话标识由雪花算法生成，为 19 位大整数，超出 JS Number 的安全整数范围
     * （2^53-1），必须以字符串形式返回，否则前端 JSON.parse 后末位被舍入，
     * 第二步查 Redis 必然 miss（"登录会话不存在或已过期"）。
     * </p>
     */
    @GetMapping("/LoginInitId")
    public Result<String> loginInitId() {
        return Result.success(String.valueOf(authService.createLoginSession()));
    }

    /**
     * 第二步：获取图形验证码
     */
    @PostMapping("/image")
    public Result<CaptchaVO> loginImage(@RequestHeader("userId") Long loginInitId) {
        return Result.success(authService.generateCaptcha(loginInitId));
    }

    /**
     * 第三步：用户提交登录
     */
    @PostMapping
    public Result<LoginVO> login(@Valid @RequestBody LoginDTO loginDTO) {
        return Result.success(authService.login(loginDTO));
    }
}
