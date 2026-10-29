package com.witpath.mq;

import com.witpath.ai.AiTaskRunner;
import com.witpath.config.RabbitConfig;
import com.witpath.service.MaterialIngestService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * 异步消息消费者
 * <p>
 * 所有消费者只做一件事：把消息转交给对应的执行器。业务逻辑集中放在执行器中，
 * 便于 RabbitMQ 通道与本地降级通道复用同一份实现。
 * </p>
 * <p>
 * 幂等说明：执行器内部会判断任务状态，已进入终态的任务不会被重复处理。
 * </p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TaskConsumers {

    private final AiTaskRunner aiTaskRunner;
    private final MaterialIngestService materialIngestService;

    @RabbitListener(queues = RabbitConfig.QUEUE_AI_TASK, concurrency = "2-4")
    public void consumeAiTask(AiTaskMessage message) {
        if (message == null || message.taskNo() == null) {
            log.warn("收到非法 AI 任务消息：{}", message);
            return;
        }
        log.info("消费 AI 任务消息：taskNo={}", message.taskNo());
        aiTaskRunner.execute(message.taskNo());
    }

    @RabbitListener(queues = RabbitConfig.QUEUE_DOC_INGEST, concurrency = "1-2")
    public void consumeDocIngest(DocIngestMessage message) {
        if (message == null || message.materialId() == null) {
            log.warn("收到非法文档消息：{}", message);
            return;
        }
        log.info("消费文档解析消息：materialId={}", message.materialId());
        materialIngestService.ingest(message.materialId(), message.userId());
    }
}
