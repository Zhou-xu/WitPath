package com.witpath.mq;

import com.witpath.config.RabbitConfig;
import com.witpath.ai.AiTaskRunner;
import com.witpath.service.MaterialIngestService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 任务分发器：优先走 RabbitMQ，Broker 不可用时自动降级到本地线程池
 * <p>
 * 降级判定：通过一次真实发送尝试探测连通性，结果缓存在内存中，避免每次请求都等待超时。
 * 重试策略：Broker 恢复后，下一次发送成功即自动切回 MQ 通道。
 * </p>
 */
@Slf4j
@Component
public class TaskDispatcher {

    private final RabbitTemplate rabbitTemplate;
    private final AiTaskRunner aiTaskRunner;
    private final MaterialIngestService materialIngestService;
    private final ExecutorService aiTaskExecutor;

    @Value("${witpath.mq.enabled:true}")
    private boolean mqEnabled;

    /** 上次探测结果 */
    private final AtomicBoolean brokerAvailable = new AtomicBoolean(true);

    public TaskDispatcher(RabbitTemplate rabbitTemplate,
                          AiTaskRunner aiTaskRunner,
                          MaterialIngestService materialIngestService,
                          @org.springframework.beans.factory.annotation.Qualifier("aiTaskExecutor")
                          ExecutorService aiTaskExecutor) {
        this.rabbitTemplate = rabbitTemplate;
        this.aiTaskRunner = aiTaskRunner;
        this.materialIngestService = materialIngestService;
        this.aiTaskExecutor = aiTaskExecutor;
    }

    /**
     * 分发 AI 分析任务
     */
    public void dispatchAiTask(AiTaskMessage message) {
        if (tryPublish(RabbitConfig.RK_AI_TASK, message, "AI 任务")) {
            return;
        }
        log.warn("RabbitMQ 不可用，AI 任务转本地线程池执行：taskNo={}", message.taskNo());
        aiTaskExecutor.submit(() -> aiTaskRunner.execute(message.taskNo()));
    }

    /**
     * 分发文档解析任务
     */
    public void dispatchDocIngest(DocIngestMessage message) {
        if (tryPublish(RabbitConfig.RK_DOC_INGEST, message, "文档 ingest")) {
            return;
        }
        log.warn("RabbitMQ 不可用，文档任务转本地线程池执行：materialId={}", message.materialId());
        aiTaskExecutor.submit(() -> materialIngestService.ingest(message.materialId(), message.userId()));
    }

    /**
     * 返回是否成功投递到 MQ
     */
    private boolean tryPublish(String routingKey, Object payload, String label) {
        if (!mqEnabled) {
            return false;
        }
        try {
            rabbitTemplate.convertAndSend(RabbitConfig.EXCHANGE, routingKey, payload);
            if (!brokerAvailable.get()) {
                log.info("RabbitMQ 已恢复，恢复使用消息队列通道");
            }
            brokerAvailable.set(true);
            return true;
        } catch (AmqpException e) {
            brokerAvailable.set(false);
            log.warn("投递 {} 到 RabbitMQ 失败：{}", label, e.getMessage());
            return false;
        }
    }

    /**
     * 当前 MQ 连通性（供健康检查接口展示）
     */
    public boolean isBrokerAvailable() {
        return mqEnabled && brokerAvailable.get();
    }
}
