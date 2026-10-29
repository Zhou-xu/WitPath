package com.witpath.ai;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.witpath.entity.AiTask;
import com.witpath.entity.Reminder;
import com.witpath.enumeration.AiStage;
import com.witpath.enumeration.AiTaskStatus;
import com.witpath.mapper.AiTaskMapper;
import com.witpath.mapper.ReminderMapper;
import com.witpath.utils.JsonUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * AI 任务状态写入器（只依赖 Mapper，避免与 Service 形成循环依赖）
 * <p>
 * 每次状态更新都是独立的短事务（REQUIRES_NEW）：
 * 一次 AI 分析可能耗时数十秒到数分钟，若把整段执行包在一个事务里，
 * 行锁会长期占用，AI 服务的进度回调会被阻塞甚至超时。
 * 拆分后回调可以实时写入，前端轮询即可看到进度变化。
 * </p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AiTaskStateWriter {

    private final AiTaskMapper aiTaskMapper;
    private final ReminderMapper reminderMapper;

    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public void markRunning(String taskNo) {
        AiTask task = findByTaskNo(taskNo);
        if (task == null) {
            return;
        }
        AiTask update = new AiTask();
        update.setId(task.getId());
        update.setStatus(AiTaskStatus.RUNNING.getCode());
        update.setStartedAt(LocalDateTime.now());
        aiTaskMapper.updateById(update);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public void markSucceeded(String taskNo, AiAnalyzeResponse response, int latency) {
        AiTask task = findByTaskNo(taskNo);
        if (task == null) {
            return;
        }
        AiTask update = new AiTask();
        update.setId(task.getId());
        update.setStatus(AiTaskStatus.SUCCEEDED.getCode());
        update.setStage(AiStage.SUMMARY.getKey());
        update.setStageMessage("已完成");
        update.setProgress(100);
        update.setSummary(response.getSummary());
        update.setSuggestion(response.getSuggestion());
        update.setResultJson(JsonUtils.toJson(response));
        update.setModel(response.getModel());
        update.setTokensUsed(response.getTokensUsed() == null ? 0 : response.getTokensUsed());
        update.setLatencyMs(latency);
        update.setFinishedAt(LocalDateTime.now());
        aiTaskMapper.updateById(update);

        createInsightReminder(task, response);
        log.info("AI 任务执行成功：taskNo={} model={} tokens={} latency={}ms",
                taskNo, response.getModel(), response.getTokensUsed(), latency);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public void markFailed(String taskNo, String errorMessage, int latency) {
        AiTask task = findByTaskNo(taskNo);
        if (task == null) {
            return;
        }
        AiTask update = new AiTask();
        update.setId(task.getId());
        update.setStatus(AiTaskStatus.FAILED.getCode());
        update.setProgress(0);
        update.setStageMessage("执行失败");
        update.setErrorMessage(truncate(errorMessage, 900));
        update.setLatencyMs(latency);
        update.setFinishedAt(LocalDateTime.now());
        aiTaskMapper.updateById(update);
    }

    private AiTask findByTaskNo(String taskNo) {
        return aiTaskMapper.selectOne(new LambdaQueryWrapper<AiTask>().eq(AiTask::getTaskNo, taskNo));
    }

    /** 分析完成后生成一条 AI 建议提醒，便于在提醒中心回溯 */
    private void createInsightReminder(AiTask task, AiAnalyzeResponse response) {
        try {
            if (response.getSummary() == null || response.getSummary().isBlank()) {
                return;
            }
            Reminder reminder = new Reminder();
            reminder.setUserId(task.getUserId());
            reminder.setType("AI_SUGGESTION");
            reminder.setLevel(1);
            reminder.setTitle("AI 学习建议已生成");
            reminder.setContent(truncate(response.getSummary(), 400));
            reminder.setSource("AI");
            reminder.setStatus(0);
            reminder.setTriggerTime(LocalDateTime.now());
            reminder.setIsDeleted(0);
            reminderMapper.insert(reminder);
        } catch (Exception e) {
            log.warn("写入 AI 建议提醒失败：{}", e.getMessage());
        }
    }

    private static String truncate(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max);
    }
}
