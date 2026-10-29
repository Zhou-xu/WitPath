package com.witpath.controller;

import com.witpath.dto.RegisterDTO;
import com.witpath.result.Result;
import com.witpath.service.AuthService;
import com.witpath.vo.UserVO;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 用户注册控制器
 */
@RestController
@RequestMapping("/register")
@RequiredArgsConstructor
public class RegisterController {

    private final AuthService authService;

    @PostMapping
    public Result<UserVO> register(@Valid @RequestBody RegisterDTO dto, HttpServletRequest request) {
        return Result.success(authService.register(dto, resolveClientIp(dto, request)));
    }

    /**
     * 解析注册用户 IP：前端通过公网探测服务获取的 IP 最接近真实公网地址，优先采用；
     * 前端获取失败时依次取反向代理透传头与直连地址兜底（本地开发场景为内网/环回地址）。
     */
    private String resolveClientIp(RegisterDTO dto, HttpServletRequest request) {
        if (dto.getClientIp() != null && !dto.getClientIp().isBlank()) {
            return dto.getClientIp().trim();
        }
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            // 多级代理时 X-Forwarded-For 为逗号串联，第一段是最原始的客户端 IP
            return forwarded.split(",")[0].trim();
        }
        String realIp = request.getHeader("X-Real-IP");
        if (realIp != null && !realIp.isBlank()) {
            return realIp.trim();
        }
        return request.getRemoteAddr();
    }
}
