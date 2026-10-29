package com.witpath.mq;

import java.io.Serializable;

/**
 * AI 任务消息体
 *
 * @param taskNo 任务编号
 * @param userId 发起用户
 * @param type   任务类型
 */
public record AiTaskMessage(String taskNo, Long userId, String type) implements Serializable {
}
