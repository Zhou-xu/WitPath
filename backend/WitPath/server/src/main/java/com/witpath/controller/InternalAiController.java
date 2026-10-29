package com.witpath.controller;

import com.witpath.constant.Constants;
import com.witpath.dto.AiProgressDTO;
import com.witpath.exception.BusinessException;
import com.witpath.properties.AiServiceProperties;
import com.witpath.result.Result;
import com.witpath.service.AiTaskService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * AI 服务进度回调接口
 * <p>
 * FastAPI 在执行 LangGraph 各节点时回调本接口，用于向前端提供阶段性进度。
 * 该接口不参与 JWT 鉴权，改用内部令牌校验，且必须在 Spring Security 白名单中放行。
 * </p>
 */
@Slf4j
@RestController
@RequestMapping("/internal/ai")
@RequiredArgsConstructor
public class InternalAiController {

    private final AiTaskService aiTaskService;
    private final AiServiceProperties aiServiceProperties;

    @PostMapping("/progress")
    public Result<Boolean> progress(@RequestHeader(value = Constants.HEADER_AI_CALLBACK_TOKEN, required = false)
                                    String token,
                                    @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false)
                                    String authorization,
                                    @RequestBody AiProgressDTO dto) {
        String expected = aiServiceProperties.getCallbackToken();
        String provided = token != null ? token : stripBearer(authorization);
        if (expected == null || expected.isBlank() || !expected.equals(provided)) {
            log.warn("AI 进度回调令牌校验失败：taskNo={}", dto == null ? null : dto.getTaskNo());
            throw new BusinessException(403, "回调令牌无效");
        }
        aiTaskService.updateProgress(dto);
        return Result.success(true);
    }

    private static String stripBearer(String authorization) {
        if (authorization != null && authorization.startsWith("Bearer ")) {
            return authorization.substring(7);
        }
        return authorization;
    }
}
