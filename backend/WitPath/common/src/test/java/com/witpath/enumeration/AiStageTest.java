package com.witpath.enumeration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * AI 阶段枚举与 FastAPI / LangGraph 侧节点名的对齐关系。
 */
class AiStageTest {

    @Test
    @DisplayName("节点名可与 LangGraph 上报的 key 双向映射")
    void ofMapsGraphNodeKeys() {
        assertEquals(AiStage.DATA_COLLECT, AiStage.of("data_collect"));
        assertEquals(AiStage.ANALYSIS, AiStage.of("analysis"));
        assertEquals(AiStage.PLANNING, AiStage.of("planning"));
        assertEquals(AiStage.RETRIEVAL, AiStage.of("retrieval"));
        assertEquals(AiStage.ADVICE, AiStage.of("advice"));
        assertEquals(AiStage.SUMMARY, AiStage.of("summary"));
        assertEquals(AiStage.QUEUED, AiStage.of("queued"));
    }

    @Test
    @DisplayName("未知节点名返回 null，大小写不敏感")
    void unknownKeyReturnsNull() {
        assertNull(AiStage.of("not_exist"));
        assertNull(AiStage.of(null));
        assertEquals(AiStage.SUMMARY, AiStage.of("Summary"));
    }

    @Test
    @DisplayName("进度百分比随流程单调递增，终态为 100")
    void progressIsMonotonic() {
        int previous = -1;
        for (AiStage stage : AiStage.values()) {
            assertTrue(stage.progress() > previous, stage.name() + " 进度应递增");
            previous = stage.progress();
        }
        assertEquals(0, AiStage.QUEUED.progress());
        assertEquals(100, AiStage.SUMMARY.progress());
    }
}
