package com.witpath.mq;

import java.io.Serializable;

/**
 * 文档解析 / 向量化消息体
 *
 * @param materialId 资料 ID
 * @param userId     所属用户
 */
public record DocIngestMessage(Long materialId, Long userId) implements Serializable {
}
