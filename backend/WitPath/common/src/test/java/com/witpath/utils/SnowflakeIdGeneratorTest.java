package com.witpath.utils;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class SnowflakeIdGeneratorTest {

    @Test
    @DisplayName("同一节点连续生成的 ID 递增且不重复")
    void nextIdIsMonotonicAndUnique() {
        SnowflakeIdGenerator generator = new SnowflakeIdGenerator(1, 1);
        Set<Long> ids = new HashSet<>();
        long previous = Long.MIN_VALUE;

        for (int i = 0; i < 10000; i++) {
            long id = generator.nextId();
            assertTrue(id > previous, "ID 应随时间/序列递增");
            assertTrue(ids.add(id), "ID 不应重复");
            previous = id;
        }
        assertEquals(10000, ids.size());
    }

    @Test
    @DisplayName("不同节点生成的 ID 不会冲突")
    void differentNodesDoNotConflict() {
        SnowflakeIdGenerator a = new SnowflakeIdGenerator(1, 1);
        SnowflakeIdGenerator b = new SnowflakeIdGenerator(2, 1);
        assertNotEquals(a.nextId(), b.nextId());
    }

    @Test
    @DisplayName("节点编号越界时构造失败")
    void invalidWorkerIdRejected() {
        assertThrows(IllegalArgumentException.class, () -> new SnowflakeIdGenerator(32, 1));
        assertThrows(IllegalArgumentException.class, () -> new SnowflakeIdGenerator(1, 32));
    }
}
