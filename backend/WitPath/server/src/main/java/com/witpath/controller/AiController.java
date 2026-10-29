package com.witpath.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.witpath.ai.AiGateway;
import com.witpath.context.UserContext;
import com.witpath.dto.AiAnalysisDTO;
import com.witpath.dto.ChatDTO;
import com.witpath.dto.PageQueryDTO;
import com.witpath.result.PageResult;
import com.witpath.result.Result;
import com.witpath.service.AiTaskService;
import com.witpath.vo.AiTaskVO;
import com.witpath.vo.ChatMessageVO;
import com.witpath.vo.ConversationVO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * AI 能力与助手控制器
 */
@RestController
@RequestMapping("/api/ai")
@RequiredArgsConstructor
public class AiController {

    private final AiTaskService aiTaskService;
    private final AiGateway aiGateway;

    /** 提交学习分析任务（异步） */
    @PostMapping("/analyze")
    public Result<AiTaskVO> analyze(@RequestBody AiAnalysisDTO dto) {
        return Result.success(aiTaskService.submit(UserContext.requireUserId(), dto));
    }

    /** 查询任务进度 / 结果 */
    @GetMapping("/tasks/{taskNo}")
    public Result<AiTaskVO> taskDetail(@PathVariable String taskNo) {
        return Result.success(aiTaskService.detail(UserContext.requireUserId(), taskNo));
    }

    /** 任务列表 */
    @GetMapping("/tasks")
    public Result<PageResult<AiTaskVO>> tasks(PageQueryDTO query,
                                              @RequestParam(value = "type", required = false) String type) {
        return Result.success(aiTaskService.page(UserContext.requireUserId(), query, type));
    }

    /** 最近一条已完成的分析 */
    @GetMapping("/tasks/latest")
    public Result<AiTaskVO> latest(@RequestParam(value = "type", required = false) String type) {
        return Result.success(aiTaskService.latest(UserContext.requireUserId(), type));
    }

    /** AI 助手对话（同步） */
    @PostMapping("/chat")
    public Result<ChatMessageVO> chat(@RequestBody ChatDTO dto) {
        return Result.success(aiTaskService.chat(UserContext.requireUserId(), dto));
    }

    @GetMapping("/conversations")
    public Result<List<ConversationVO>> conversations() {
        return Result.success(aiTaskService.conversations(UserContext.requireUserId()));
    }

    @GetMapping("/conversations/{id}/messages")
    public Result<List<ChatMessageVO>> messages(@PathVariable Long id) {
        return Result.success(aiTaskService.messages(UserContext.requireUserId(), id));
    }

    @DeleteMapping("/conversations/{id}")
    public Result<Boolean> removeConversation(@PathVariable Long id) {
        aiTaskService.removeConversation(UserContext.requireUserId(), id);
        return Result.success(true);
    }

    /** 知识检索（转发 AI 服务的 Milvus 检索） */
    @GetMapping("/search")
    public Result<JsonNode> search(@RequestParam String q,
                                   @RequestParam(value = "topK", defaultValue = "5") int topK) {
        return Result.success(aiGateway.search(UserContext.requireUserId(), q, topK));
    }
}
