package com.witpath.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * RabbitMQ 交换器 / 队列 / 绑定关系配置
 * <p>
 * 拓扑设计：
 * <pre>
 *   exchange: witpath.topic  (durable, topic)
 *     ├── routing: ai.task.submit   -> queue: q.witpath.ai.task       (死信 -> q.witpath.ai.task.dlx)
 *     └── routing: doc.ingest       -> queue: q.witpath.doc.ingest    (死信 -> q.witpath.doc.ingest.dlx)
 * </pre>
 * 队列均为持久化队列，且配置了死信交换机，失败消息不会丢失。
 * </p>
 * <p>
 * 说明：Spring AMQP 的声明动作发生在连接建立之后，因此 RabbitMQ 未启动时应用仍可正常启动，
 * 只是队列尚未创建；业务层会根据连通性自动降级为本地异步执行。
 * </p>
 */
@Slf4j
@Configuration
public class RabbitConfig {

    public static final String EXCHANGE = "witpath.topic";
    public static final String QUEUE_AI_TASK = "q.witpath.ai.task";
    public static final String QUEUE_AI_TASK_DLX = QUEUE_AI_TASK + ".dlx";
    public static final String QUEUE_DOC_INGEST = "q.witpath.doc.ingest";
    public static final String QUEUE_DOC_INGEST_DLX = QUEUE_DOC_INGEST + ".dlx";
    public static final String RK_AI_TASK = "ai.task.submit";
    public static final String RK_DOC_INGEST = "doc.ingest";
    public static final String DLX_ROUTING_KEY = "#";

    @Value("${witpath.mq.enabled:true}")
    private boolean mqEnabled;

    @Bean
    public TopicExchange witpathExchange() {
        return new TopicExchange(EXCHANGE, true, false);
    }

    @Bean
    public Queue aiTaskQueue() {
        return QueueBuilder.durable(QUEUE_AI_TASK)
                .deadLetterExchange(EXCHANGE)
                .deadLetterRoutingKey(QUEUE_AI_TASK_DLX)
                .build();
    }

    @Bean
    public Queue aiTaskDlxQueue() {
        return QueueBuilder.durable(QUEUE_AI_TASK_DLX).build();
    }

    @Bean
    public Queue docIngestQueue() {
        return QueueBuilder.durable(QUEUE_DOC_INGEST)
                .deadLetterExchange(EXCHANGE)
                .deadLetterRoutingKey(QUEUE_DOC_INGEST_DLX)
                .build();
    }

    @Bean
    public Queue docIngestDlxQueue() {
        return QueueBuilder.durable(QUEUE_DOC_INGEST_DLX).build();
    }

    @Bean
    public Binding aiTaskBinding() {
        return BindingBuilder.bind(aiTaskQueue()).to(witpathExchange()).with(RK_AI_TASK);
    }

    @Bean
    public Binding aiTaskDlxBinding() {
        return BindingBuilder.bind(aiTaskDlxQueue()).to(witpathExchange()).with(QUEUE_AI_TASK_DLX);
    }

    @Bean
    public Binding docIngestBinding() {
        return BindingBuilder.bind(docIngestQueue()).to(witpathExchange()).with(RK_DOC_INGEST);
    }

    @Bean
    public Binding docIngestDlxBinding() {
        return BindingBuilder.bind(docIngestDlxQueue()).to(witpathExchange()).with(QUEUE_DOC_INGEST_DLX);
    }

    /**
     * 使用 JSON 序列化消息，便于跨语言消费与人工排查
     */
    @Bean
    public MessageConverter jackson2JsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    /**
     * 忽略声明异常，保证 RabbitMQ 不可用时应用依然能够启动
     */
    @Bean
    public RabbitAdmin rabbitAdmin(org.springframework.amqp.rabbit.connection.ConnectionFactory connectionFactory) {
        RabbitAdmin admin = new RabbitAdmin(connectionFactory);
        admin.setIgnoreDeclarationExceptions(true);
        admin.setAutoStartup(mqEnabled);
        return admin;
    }
}
