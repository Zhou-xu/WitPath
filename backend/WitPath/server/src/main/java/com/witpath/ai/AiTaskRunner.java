package com.witpath.ai;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.witpath.ai.AiGateway.AiServiceException;
import com.witpath.constant.Constants;
import com.witpath.dto.AiContextPayload;
import com.witpath.entity.AiTask;
import com.witpath.enumeration.AiTaskStatus;
import com.witpath.mapper.AiTaskMapper;
import com.witpath.utils.JsonUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * AI 任务执行器
 * <p>
 * RabbitMQ 消费者与本地降级执行器共用同一段执行逻辑，保证两条通道行为一致：
 * 1. 标记任务为执行中；
 * 2. 装配用户学习数据 -> 调用 FastAPI（LangGraph 多 Agent）；
 * 3. 回写结构化结果，并生成一条 AI 建议类提醒。
 * </p>
 * <p>
 * 幂等保证：仅当任务处于 QUEUED / RUNNING 状态时才执行，避免重复消费导致重复分析。
 * 事务边界：本方法不开启事务，状态更新委托给 {@link AiTaskStateWriter} 的短事务方法，
 * 避免长时间持有行锁（一次分析可能耗时数十秒），使 AI 服务的进度回调能够实时生效。
 * </p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AiTaskRunner {

    private final AiTaskMapper aiTaskMapper;
    private final AiGateway aiGateway;
    private final StudyDataAssembler dataAssembler;
    private final AiTaskStateWriter stateWriter;

    /**
     * 执行指定任务，异常向上抛出由调用方处理（消费者可据此触发重试 / 死信）
     */
    public void execute(String taskNo) {
        AiTask task = aiTaskMapper.selectOne(new LambdaQueryWrapper<AiTask>().eq(AiTask::getTaskNo, taskNo));
        if (task == null) {
            log.warn("AI 任务不存在，跳过执行：taskNo={}", taskNo);
            return;
        }
        // 幂等：已进入终态的任务不再重复处理
        if (AiTaskStatus.isFinished(task.getStatus())) {
            log.info("AI 任务已处于终态，跳过执行：taskNo={} status={}", taskNo, task.getStatus());
            return;
        }

        long begin = System.currentTimeMillis();
        stateWriter.markRunning(taskNo);

        try {
            AiContextPayload payload = dataAssembler.assemble(
                    task.getUserId(), taskNo, task.getType(), task.getQuestion(),
                    parseRangeDays(task), task.getFocusGoalId(),
                    aiGateway.callbackBaseUrl() + Constants.PATH_AI_PROGRESS, aiGateway.callbackToken());

            AiAnalyzeResponse response;
            try {
                response = aiGateway.analyze(payload);
            } catch (AiServiceException e) {
                stateWriter.markFailed(taskNo, e.getMessage(), (int) (System.currentTimeMillis() - begin));
                log.error("AI 服务调用失败：taskNo={} msg={}", taskNo, e.getMessage());
                return;
            }

            if (response == null) {
                stateWriter.markFailed(taskNo, "AI 服务返回空结果", (int) (System.currentTimeMillis() - begin));
                return;
            }
            if (response.getError() != null && !response.getError().isBlank()) {
                stateWriter.markFailed(taskNo, response.getError(), (int) (System.currentTimeMillis() - begin));
                return;
            }

            stateWriter.markSucceeded(taskNo, response, (int) (System.currentTimeMillis() - begin));
        } catch (Exception e) {
            stateWriter.markFailed(taskNo, e.getMessage(), (int) (System.currentTimeMillis() - begin));
            log.error("AI 任务执行异常：taskNo={}", taskNo, e);
        }
    }

    private Integer parseRangeDays(AiTask task) {
        AiContextPayload payload = JsonUtils.fromJson(task.getRequestJson(), AiContextPayload.class);
        if (payload != null && payload.getSummary() != null) {
            return payload.getSummary().getRangeDays();
        }
        return 14;
    }
}
