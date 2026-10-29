package com.witpath.utils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

/**
 * 日期区间工具，用于趋势图、热力图等需要补全连续日期的场景
 */
public final class DateUtils {

    public static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private DateUtils() {
    }

    /**
     * 生成 [start, end] 区间内的连续日期列表
     */
    public static List<LocalDate> range(LocalDate start, LocalDate end) {
        List<LocalDate> days = new ArrayList<>();
        if (start == null || end == null || end.isBefore(start)) {
            return days;
        }
        for (LocalDate day = start; !day.isAfter(end); day = day.plusDays(1)) {
            days.add(day);
        }
        return days;
    }

    /**
     * 计算两个自然日之间的天数差，负数表示已逾期
     */
    public static long betweenDays(LocalDate start, LocalDate end) {
        if (start == null || end == null) {
            return 0L;
        }
        return ChronoUnit.DAYS.between(start, end);
    }

    public static LocalDateTime atStartOfDay(LocalDate date) {
        return date == null ? null : date.atStartOfDay();
    }

    public static LocalDateTime atEndOfDay(LocalDate date) {
        return date == null ? null : LocalDateTime.of(date, LocalTime.MAX);
    }

    /**
     * 根据开始/结束时间推导时长（分钟），异常区间返回 0
     */
    public static int durationMinutes(LocalDateTime start, LocalDateTime end) {
        if (start == null || end == null || end.isBefore(start)) {
            return 0;
        }
        return (int) ChronoUnit.MINUTES.between(start, end);
    }
}
