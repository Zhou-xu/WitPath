package com.witpath.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.witpath.ai.AiGateway;
import com.witpath.ai.AiTaskRunner;
import com.witpath.ai.ChatRequest;
import com.witpath.ai.AiChatResponse;
import com.witpath.ai.StudyDataAssembler;
import com.witpath.context.UserContext;
import com.witpath.dto.AiAnalysisDTO;
import com.witpath.dto.AiContextPayload;
import com.witpath.dto.AiProgressDTO;
import com.witpath.dto.ChatDTO;
import com.witpath.dto.PageQueryDTO;
import com.witpath.entity.AiConversation;
import com.witpath.entity.AiMessage;
import com.witpath.entity.AiTask;
import com.witpath.entity.Reminder;
import com.witpath.entity.User;
import com.witpath.enumeration.AiStage;
import com.witpath.enumeration.AiTaskStatus;
import com.witpath.enumeration.AiTaskType;
import com.witpath.exception.BusinessException;
import com.witpath.mapper.AiConversationMapper;
import com.witpath.mapper.AiMessageMapper;
import com.witpath.mapper.AiTaskMapper;
import com.witpath.mapper.ReminderMapper;
import com.witpath.mapper.UserMapper;
import com.witpath.mq.AiTaskMessage;
import com.witpath.mq.TaskDispatcher;
import com.witpath.result.PageResult;
import com.witpath.support.VoConverter;
import com.witpath.utils.JsonUtils;
import com.witpath.vo.AiTaskVO;
import com.witpath.vo.ChatMessageVO;
import com.witpath.vo.ConversationVO;
import com.witpath.vo.DashboardVO;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * AI 任务与 AI 助手会话服务
 * <p>
 * 分析类任务为异步：落库 -> 投递消息 -> 执行器调用 FastAPI -> 回写结果。
 * 助手对话为同步：组装上下文 -> 调用 FastAPI -> 落库并返回。
 * </p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiTaskService {

    private static final int MAX_HISTORY = 10;

    private final AiTaskMapper aiTaskMapper;
    private final ReminderMapper reminderMapper;
    private final AiConversationMapper conversationMapper;
    private final AiMessageMapper messageMapper;
    private final UserMapper userMapper;
    private final TaskDispatcher taskDispatcher;
    private final AiGateway aiGateway;
    private final DashboardService dashboardService;

    /**
     * 提交 AI 分析任务
     */
    @Transactional(rollbackFor = Exception.class)
    public AiTaskVO submit(Long userId, AiAnalysisDTO dto) {
        String type = AiTaskType.of(dto.getType()).getCode();
        String taskNo = UUID.randomUUID().toString();

        AiTask task = new AiTask();
        task.setTaskNo(taskNo);
        task.setUserId(userId);
        task.setType(type);
        task.setQuestion(dto.getQuestion());
        task.setFocusGoalId(dto.getGoalId());
        task.setStatus(AiTaskStatus.QUEUED.getCode());
        task.setStage(AiStage.QUEUED.getKey());
        task.setStageMessage("已提交，等待调度");
        task.setProgress(0);
        task.setIsDeleted(0);
        task.setTitle(AiTaskType.of(type).getDesc());

        AiContextPayload preview = new AiContextPayload();
        preview.setSummary(new AiContextPayload.Summary());
        preview.getSummary().setRangeDays(dto.getRangeDays() == null ? 14 : dto.getRangeDays());
        task.setRequestJson(JsonUtils.toJson(preview));

        aiTaskMapper.insert(task);
        // 必须在事务提交后再投递消息：否则消费者可能先于提交被触发，
        // 查不到这条任务而被误判为「任务不存在」并丢弃。
        dispatchAfterCommit(taskNo, userId, type);
        log.info("提交 AI 任务：taskNo={} userId={} type={}", taskNo, userId, type);
        return toVO(task);
    }

    /**
     * 事务提交后再投递任务消息。
     * <p>
     * 若当前没有事务（例如单元测试直调），则立即投递，保证行为一致。
     * </p>
     */
    private void dispatchAfterCommit(String taskNo, Long userId, String type) {
        AiTaskMessage message = new AiTaskMessage(taskNo, userId, type);
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    taskDispatcher.dispatchAiTask(message);
                }
            });
        } else {
            taskDispatcher.dispatchAiTask(message);
        }
    }

    /**
     * 任务详情（支持按编号查询，前端轮询此接口获取进度）
     */
    public AiTaskVO detail(Long userId, String taskNo) {
        AiTask task = aiTaskMapper.selectOne(new LambdaQueryWrapper<AiTask>()
                .eq(AiTask::getTaskNo, taskNo)
                .eq(AiTask::getUserId, userId));
        if (task == null) {
            throw new BusinessException(404, "任务不存在");
        }
        return toVO(task);
    }

    /**
     * 任务列表（默认按创建时间倒序）
     */
    public PageResult<AiTaskVO> page(Long userId, PageQueryDTO query, String type) {
        Page<AiTask> page = new Page<>(query.safePage(), query.safeSize());
        LambdaQueryWrapper<AiTask> wrapper = new LambdaQueryWrapper<AiTask>()
                .eq(AiTask::getUserId, userId)
                .eq(type != null && !type.isBlank(), AiTask::getType, type)
                .orderByDesc(AiTask::getId);
        aiTaskMapper.selectPage(page, wrapper);

        List<AiTaskVO> vos = page.getRecords().stream().map(this::toVO).toList();
        return PageResult.of(page.getCurrent(), page.getSize(), page.getTotal(), vos);
    }

    /**
     * 最近一条已完成的任务（首页展示）
     */
    public AiTaskVO latest(Long userId, String type) {
        AiTask task = aiTaskMapper.selectOne(new LambdaQueryWrapper<AiTask>()
                .eq(AiTask::getUserId, userId)
                .eq(AiTask::getStatus, AiTaskStatus.SUCCEEDED.getCode())
                .eq(type != null && !type.isBlank(), AiTask::getType, type)
                .orderByDesc(AiTask::getId)
                .last("LIMIT 1"));
        return task == null ? null : toVO(task);
    }

    /**
     * 接收 AI 服务的阶段性进度回调
     */
    @Transactional(rollbackFor = Exception.class)
    public void updateProgress(AiProgressDTO dto) {
        if (dto == null || dto.getTaskNo() == null) {
            return;
        }
        AiTask task = aiTaskMapper.selectOne(
                new LambdaQueryWrapper<AiTask>().eq(AiTask::getTaskNo, dto.getTaskNo()));
        if (task == null) {
            log.warn("收到未知任务的进度回调：taskNo={}", dto.getTaskNo());
            return;
        }
        // 任务已进入终态：迟到回调不得覆盖最终结果（否则会把已完成改回执行中）
        if (AiTaskStatus.isFinished(task.getStatus())) {
            return;
        }
        AiStage stage = AiStage.of(dto.getStage());
        int progress = dto.getProgress() != null ? dto.getProgress()
                : (stage == null ? task.getProgress() : stage.progress());

        AiTask update = new AiTask();
        update.setId(task.getId());
        if (stage != null) {
            update.setStage(stage.getKey());
            update.setStageMessage(stage.getLabel());
        }
        if (dto.getStageMessage() != null) {
            update.setStageMessage(dto.getStageMessage());
        }
        update.setProgress(Math.min(100, Math.max(0, progress)));
        if (!AiTaskStatus.isFinished(task.getStatus())) {
            update.setStatus(AiTaskStatus.RUNNING.getCode());
        }
        if (dto.getModel() != null) {
            update.setModel(dto.getModel());
        }
        aiTaskMapper.updateById(update);
        log.debug("AI 任务进度更新：taskNo={} stage={} progress={}", dto.getTaskNo(), dto.getStage(), progress);
    }

    // ------------------------------------------------------------------
    // AI 助手对话
    // ------------------------------------------------------------------

    /**
     * 发送一条消息并返回助手回复（同步）
     */
    @Transactional(rollbackFor = Exception.class)
    public ChatMessageVO chat(Long userId, ChatDTO dto) {
        AiConversation conversation = resolveConversation(userId, dto.getConversationId(), dto.getMessage());

        AiMessage userMessage = new AiMessage();
        userMessage.setConversationId(conversation.getId());
        userMessage.setUserId(userId);
        userMessage.setRole("user");
        userMessage.setContent(dto.getMessage());
        userMessage.setTokensUsed(0);
        userMessage.setIsDeleted(0);
        messageMapper.insert(userMessage);

        User user = userMapper.selectById(userId);
        List<AiMessage> history = loadHistory(conversation.getId());

        ChatRequest request = new ChatRequest();
        request.setUserId(userId);
        request.setNickname(user == null ? null : user.getNickname());
        request.setMessage(dto.getMessage());
        request.setUseKnowledge(Boolean.TRUE.equals(dto.getWithContext()));
        request.setHistory(history.stream()
                .map(m -> new ChatRequest.HistoryItem(m.getRole(), truncate(m.getContent(), 1500)))
                .toList());

        if (Boolean.TRUE.equals(dto.getWithContext())) {
            DashboardVO overview = dashboardService.overview(userId, 14);
            JsonNode context = JsonUtils.mapper().valueToTree(overview);
            request.setContext(context);
        }

        AiChatResponse response;
        try {
            response = aiGateway.chat(request);
        } catch (AiGateway.AiServiceException e) {
            throw new BusinessException(503, "AI 服务暂不可用：" + e.getMessage());
        }
        if (response == null || response.getReply() == null) {
            throw new BusinessException(503, "AI 服务未返回有效内容");
        }

        AiMessage assistantMessage = new AiMessage();
        assistantMessage.setConversationId(conversation.getId());
        assistantMessage.setUserId(userId);
        assistantMessage.setRole("assistant");
        assistantMessage.setContent(response.getReply());
        assistantMessage.setReferenced(JsonUtils.toJson(response.getReferenced()));
        assistantMessage.setTokensUsed(response.getTokensUsed() == null ? 0 : response.getTokensUsed());
        assistantMessage.setIsDeleted(0);
        messageMapper.insert(assistantMessage);

        // 更新会话统计
        AiConversation convUpdate = new AiConversation();
        convUpdate.setId(conversation.getId());
        convUpdate.setMessageCount(history.size() + 2);
        convUpdate.setLastMessageAt(LocalDateTime.now());
        if (conversation.getMessageCount() == null || conversation.getMessageCount() == 0) {
            convUpdate.setTitle(truncate(dto.getMessage(), 30));
        }
        conversationMapper.updateById(convUpdate);

        return toMessageVO(assistantMessage);
    }

    public List<ConversationVO> conversations(Long userId) {
        List<AiConversation> list = conversationMapper.selectList(new LambdaQueryWrapper<AiConversation>()
                .eq(AiConversation::getUserId, userId)
                .orderByDesc(AiConversation::getLastMessageAt)
                .orderByDesc(AiConversation::getId)
                .last("LIMIT 50"));
        return list.stream().map(c -> {
            ConversationVO vo = new ConversationVO();
            vo.setId(c.getId());
            vo.setTitle(c.getTitle());
            vo.setMessageCount(c.getMessageCount());
            vo.setLastMessageAt(c.getLastMessageAt());
            vo.setCreatedAt(c.getCreatedAt());
            return vo;
        }).toList();
    }

    public List<ChatMessageVO> messages(Long userId, Long conversationId) {
        AiConversation conversation = conversationMapper.selectById(conversationId);
        if (conversation == null || !Objects.equals(conversation.getUserId(), userId)) {
            throw new BusinessException(404, "会话不存在或无权访问");
        }
        return messageMapper.selectList(new LambdaQueryWrapper<AiMessage>()
                .eq(AiMessage::getConversationId, conversationId)
                .orderByAsc(AiMessage::getId)
                .last("LIMIT 200")).stream().map(this::toMessageVO).toList();
    }

    @Transactional(rollbackFor = Exception.class)
    public void removeConversation(Long userId, Long conversationId) {
        AiConversation conversation = conversationMapper.selectById(conversationId);
        if (conversation == null || !Objects.equals(conversation.getUserId(), userId)) {
            throw new BusinessException(404, "会话不存在或无权访问");
        }
        messageMapper.delete(new LambdaQueryWrapper<AiMessage>()
                .eq(AiMessage::getConversationId, conversationId));
        conversationMapper.deleteById(conversationId);
    }

    private AiConversation resolveConversation(Long userId, Long conversationId, String firstMessage) {
        if (conversationId != null) {
            AiConversation conversation = conversationMapper.selectById(conversationId);
            if (conversation != null && Objects.equals(conversation.getUserId(), userId)) {
                return conversation;
            }
        }
        AiConversation conversation = new AiConversation();
        conversation.setUserId(userId);
        conversation.setTitle(truncate(firstMessage, 30));
        conversation.setMessageCount(0);
        conversation.setLastMessageAt(LocalDateTime.now());
        conversation.setIsDeleted(0);
        conversationMapper.insert(conversation);
        return conversation;
    }

    private List<AiMessage> loadHistory(Long conversationId) {
        List<AiMessage> all = messageMapper.selectList(new LambdaQueryWrapper<AiMessage>()
                .eq(AiMessage::getConversationId, conversationId)
                .orderByDesc(AiMessage::getId)
                .last("LIMIT " + MAX_HISTORY));
        java.util.Collections.reverse(all);
        return all;
    }

    private AiTaskVO toVO(AiTask task) {
        AiTaskVO vo = new AiTaskVO();
        vo.setId(task.getId());
        vo.setTaskNo(task.getTaskNo());
        vo.setType(task.getType());
        vo.setTitle(task.getTitle());
        vo.setQuestion(task.getQuestion());
        vo.setStatus(task.getStatus());
        vo.setStatusText(AiTaskStatus.desc(task.getStatus()));
        vo.setStage(task.getStage());
        vo.setStageText(AiStage.of(task.getStage()) == null ? null : AiStage.of(task.getStage()).getLabel());
        vo.setStageMessage(task.getStageMessage());
        vo.setProgress(task.getProgress());
        vo.setSummary(task.getSummary());
        vo.setSuggestion(task.getSuggestion());
        vo.setResult(JsonUtils.fromJson(task.getResultJson(), JsonNode.class));
        vo.setModel(task.getModel());
        vo.setTokensUsed(task.getTokensUsed());
        vo.setLatencyMs(task.getLatencyMs());
        vo.setErrorMessage(task.getErrorMessage());
        vo.setStartedAt(task.getStartedAt());
        vo.setFinishedAt(task.getFinishedAt());
        vo.setCreatedAt(task.getCreatedAt());
        return vo;
    }

    private ChatMessageVO toMessageVO(AiMessage message) {
        ChatMessageVO vo = new ChatMessageVO();
        vo.setId(message.getId());
        vo.setConversationId(message.getConversationId());
        vo.setRole(message.getRole());
        vo.setContent(message.getContent());
        vo.setReferenced(JsonUtils.fromJson(message.getReferenced(), JsonNode.class));
        vo.setTokensUsed(message.getTokensUsed());
        vo.setCreatedAt(message.getCreatedAt());
        return vo;
    }

    private static String truncate(String value, int max) {
        if (value == null) {
            return "";
        }
        return value.length() <= max ? value : value.substring(0, max) + "...";
    }
}
