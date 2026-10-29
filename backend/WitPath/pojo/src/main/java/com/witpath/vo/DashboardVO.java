package com.witpath.vo;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * 概览页数据 VO
 */
@Data
public class DashboardVO implements Serializable {

    /** 核心指标 */
    private Overview overview = new Overview();

    /** 近 N 天学习时长趋势（日期已补全，无数据填 0） */
    private List<TrendPoint> trend = List.of();

    /** 学科分布 */
    private List<SubjectSlice> subjects = List.of();

    /** 近 N 天专注度趋势 */
    private List<TrendPoint> focusTrend = List.of();

    /** 进行中目标进度 */
    private List<GoalPoint> goals = List.of();

    /** 即将到期的任务 */
    private List<TaskVO> upcomingTasks = List.of();

    /** 最近学习记录 */
    private List<RecordVO> recentRecords = List.of();

    /** 最近一次完成的 AI 分析结论 */
    private LatestInsight latestInsight;

    /** 未读提醒数量 */
    private Long unreadReminders = 0L;

    @Data
    public static class Overview implements Serializable {
        /** 学习总时长（分钟） */
        private Integer totalMinutes = 0;
        /** 近 7 天时长（分钟） */
        private Integer last7DaysMinutes = 0;
        /** 今日时长（分钟） */
        private Integer todayMinutes = 0;
        /** 连续学习天数 */
        private Integer streakDays = 0;
        /** 记录条数 */
        private Long recordCount = 0L;
        /** 进行中目标数 */
        private Long activeGoalCount = 0L;
        /** 待办任务数 */
        private Long todoTaskCount = 0L;
        /** 已逾期任务数 */
        private Long overdueTaskCount = 0L;
        /** 完成的任务数 */
        private Long doneTaskCount = 0L;
        /** 平均专注度 */
        private BigDecimal avgFocus = BigDecimal.ZERO;
        /** 目标平均进度 */
        private BigDecimal avgGoalProgress = BigDecimal.ZERO;
    }

    @Data
    public static class TrendPoint implements Serializable {
        private LocalDate date;
        /** 学习时长（分钟） */
        private Integer minutes = 0;
        /** 记录条数 */
        private Integer records = 0;
        /** 平均专注度 */
        private BigDecimal focus = BigDecimal.ZERO;
    }

    @Data
    public static class SubjectSlice implements Serializable {
        private String subject;
        private Integer minutes = 0;
        private Long records = 0L;
        /** 占比百分比 */
        private BigDecimal ratio = BigDecimal.ZERO;
    }

    @Data
    public static class GoalPoint implements Serializable {
        private Long id;
        private String title;
        private BigDecimal progress = BigDecimal.ZERO;
        private BigDecimal targetHours = BigDecimal.ZERO;
        private BigDecimal actualHours = BigDecimal.ZERO;
        private Integer status;
        private LocalDate endDate;
    }

    @Data
    public static class LatestInsight implements Serializable {
        private Long taskId;
        private String taskNo;
        private String type;
        private String summary;
        private String suggestion;
        private LocalDate createdAt;
    }
}
