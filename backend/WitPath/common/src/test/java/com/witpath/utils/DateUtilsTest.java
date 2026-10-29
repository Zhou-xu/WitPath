package com.witpath.utils;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class DateUtilsTest {

    @Test
    @DisplayName("日期区间首尾闭合，且按天连续")
    void rangeIsInclusiveAndContinuous() {
        List<LocalDate> days = DateUtils.range(LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 3));

        assertEquals(3, days.size());
        assertEquals(LocalDate.of(2026, 3, 1), days.get(0));
        assertEquals(LocalDate.of(2026, 3, 3), days.get(2));
    }

    @Test
    @DisplayName("单日区间返回 1 天")
    void singleDayRange() {
        assertEquals(1, DateUtils.range(LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 1)).size());
    }

    @Test
    @DisplayName("非法区间（null / 结束早于开始）返回空列表")
    void invalidRangeReturnsEmpty() {
        assertTrue(DateUtils.range(null, LocalDate.of(2026, 3, 1)).isEmpty());
        assertTrue(DateUtils.range(LocalDate.of(2026, 3, 3), LocalDate.of(2026, 3, 1)).isEmpty());
    }

    @Test
    @DisplayName("天数差计算支持负数（逾期）")
    void betweenDaysSupportsNegative() {
        assertEquals(2L, DateUtils.betweenDays(LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 3)));
        assertEquals(-2L, DateUtils.betweenDays(LocalDate.of(2026, 3, 3), LocalDate.of(2026, 3, 1)));
        assertEquals(0L, DateUtils.betweenDays(null, null));
    }

    @Test
    @DisplayName("起止时刻覆盖整天")
    void startAndEndOfDay() {
        LocalDateTime start = DateUtils.atStartOfDay(LocalDate.of(2026, 3, 1));
        LocalDateTime end = DateUtils.atEndOfDay(LocalDate.of(2026, 3, 1));

        assertEquals(LocalDateTime.of(2026, 3, 1, 0, 0), start);
        assertTrue(end.isAfter(start));
        assertNull(DateUtils.atStartOfDay(null));
    }

    @Test
    @DisplayName("时长计算异常区间返回 0")
    void durationMinutesGuardsInvalidInput() {
        LocalDateTime start = LocalDateTime.of(2026, 3, 1, 9, 0);
        assertEquals(90, DateUtils.durationMinutes(start, start.plusMinutes(90)));
        assertEquals(0, DateUtils.durationMinutes(start, start.minusMinutes(1)));
        assertEquals(0, DateUtils.durationMinutes(null, start));
    }
}
