package com.witpath.controller;

import com.witpath.context.UserContext;
import com.witpath.dto.AiModelsDTO;
import com.witpath.dto.UserAiConfigDTO;
import com.witpath.result.Result;
import com.witpath.service.UserAiConfigService;
import com.witpath.vo.ModelOptionVO;
import com.witpath.vo.UserAiConfigVO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 用户个人大模型配置
 * <p>
 * 安全约定：
 * <ol>
 *   <li>API Key 明文只在请求体中出现，落库前 AES-GCM 加密，出库一律掩码；</li>
 *   <li>模型列表由后端代理拉取，Key 不出现在浏览器侧；</li>
 *   <li>解密结果在 Redis 缓存 5 分钟，避免每次 AI 调用都读库解密。</li>
 * </ol>
 * </p>
 */
@RestController
@RequestMapping("/api/user/ai-config")
@RequiredArgsConstructor
public class UserAiConfigController {

    private final UserAiConfigService userAiConfigService;

    /** 当前配置（API Key 为掩码） */
    @GetMapping
    public Result<UserAiConfigVO> get() {
        return Result.success(userAiConfigService.get(UserContext.requireUserId()));
    }

    /** 保存配置（API Key 留空表示不修改已保存的值） */
    @PutMapping
    public Result<UserAiConfigVO> save(@RequestBody UserAiConfigDTO request) {
        return Result.success(userAiConfigService.save(UserContext.requireUserId(), request));
    }

    /**
     * 切换回系统默认模型（配置行保留，可随时改回个人配置）
     */
    @PostMapping("/use-default")
    public Result<UserAiConfigVO> useDefault() {
        return Result.success(userAiConfigService.useDefault(UserContext.requireUserId()));
    }

    /** 删除配置，恢复使用系统默认模型 */
    @DeleteMapping
    public Result<Boolean> delete() {
        userAiConfigService.delete(UserContext.requireUserId());
        return Result.success(true);
    }

    /** 拉取用户端点上的模型列表（后端代理） */
    @PostMapping("/models")
    public Result<List<ModelOptionVO>> models(@RequestBody AiModelsDTO request) {
        return Result.success(userAiConfigService.fetchModels(UserContext.requireUserId(), request));
    }
}
