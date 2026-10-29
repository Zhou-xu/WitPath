package com.witpath.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.witpath.constant.Constants;
import com.witpath.entity.AiTask;
import com.witpath.entity.StudyGoal;
import com.witpath.entity.StudyRecord;
import com.witpath.entity.StudyTask;
import com.witpath.entity.User;
import com.witpath.enumeration.AiTaskStatus;
import com.witpath.enumeration.GoalStatus;
import com.witpath.enumeration.TaskStatus;
import com.witpath.mapper.AiTaskMapper;
import com.witpath.mapper.ReminderMapper;
import com.witpath.mapper.StudyGoalMapper;
import com.witpath.mapper.StudyRecordMapper;
import com.witpath.mapper.StudyTaskMapper;
import com.witpath.mapper.UserMapper;
import com.witpath.support.VoConverter;
import com.witpath.utils.DateUtils;
import com.witpath.utils.JsonUtils;
import com.witpath.vo.DashboardVO;
import com.witpath.vo.RecordVO;
import com.witpath.vo.TaskVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RBucket;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * 概览（Dashboard）聚合服务
 * <p>
 * 统计口径说明：
 * - 单次查询仅扫描近 365 天记录，避免全表扫描（个人学习数据量级通常为千级）。
 * - 结果写入 Redis 缓存 60 秒，学习记录变更时由 RecordService 主动失效。
 * </p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DashboardService {

    /** 缓存有效期（秒） */
    private static final long CACHE_TTL = 60L;

    private final UserMapper userMapper;
    private final StudyRecordMapper recordMapper;
    private final StudyGoalMapper goalMapper;
    private final StudyTaskMapper taskMapper;
    private final AiTaskMapper aiTaskMapper;
    private final ReminderMapper reminderMapper;
    private final RecordService recordService;
    private final TaskService taskService;
    private final RedissonClient redissonClient;

    /**
     * 概览数据（带缓存）
     */
    public DashboardVO overview(Long userId, Integer trendDays) {
        int days = (trendDays == null || trendDays <= 0) ? 14 : Math.min(trendDays, 60);

        RBucket<String> bucket = redissonClient.getBucket(Constants.REDIS_DASHBOARD_KEY + userId + ":" + days);
        try {
            String cached = bucket.get();
            if (cached != null) {
                DashboardVO cachedVO = JsonUtils.fromJson(cached, DashboardVO.class);
                if (cachedVO != null) {
                    return cachedVO;
                }
            }
        } catch (Exception e) {
            log.warn("读取概览缓存失败（将重新计算）：{}", e.getMessage());
        }

        DashboardVO vo = build(userId, days);
        try {
            bucket.set(JsonUtils.toJson(vo), CACHE_TTL, TimeUnit.SECONDS);
        } catch (Exception e) {
            log.warn("写入概览缓存失败：{}", e.getMessage());
        }
        return vo;
    }

    private DashboardVO build(Long userId, int days) {
        LocalDate today = LocalDate.now();
        LocalDate trendFrom = today.minusDays(days - 1L);
        LocalDate scanFrom = today.minusDays(365);

        User user = userMapper.selectById(userId);
        List<StudyRecord> records = recordMapper.selectList(new LambdaQueryWrapper<StudyRecord>()
                .select(StudyRecord::getId, StudyRecord::getStudyDate, StudyRecord::getDurationMinutes,
                        StudyRecord::getSubject, StudyRecord::getFocusScore, StudyRecord::getGoalId,
                        StudyRecord::getTaskId, StudyRecord::getTitle, StudyRecord::getContent,
                        StudyRecord::getStartAt, StudyRecord::getEndAt, StudyRecord::getOutcome,
                        StudyRecord::getTags)
                .eq(StudyRecord::getUserId, userId)
                .ge(StudyRecord::getStudyDate, scanFrom)
                .orderByDesc(StudyRecord::getStudyDate));

        DashboardVO vo = new DashboardVO();
        DashboardVO.Overview overview = vo.getOverview();
        overview.setRecordCount((long) records.size());

        int totalMinutes = records.stream().mapToInt(r -> safe(r.getDurationMinutes())).sum();
        overview.setTotalMinutes(totalMinutes);

        int last7 = records.stream()
                .filter(r -> r.getStudyDate() != null && !r.getStudyDate().isBefore(today.minusDays(6)))
                .mapToInt(r -> safe(r.getDurationMinutes())).sum();
        overview.setLast7DaysMinutes(last7);

        int todayMinutes = records.stream()
                .filter(r -> Objects.equals(r.getStudyDate(), today))
                .mapToInt(r -> safe(r.getDurationMinutes())).sum();
        overview.setTodayMinutes(todayMinutes);

        double avgFocus = records.stream()
                .filter(r -> r.getFocusScore() != null && r.getFocusScore() > 0)
                .mapToInt(StudyRecord::getFocusScore).average().orElse(0d);
        overview.setAvgFocus(BigDecimal.valueOf(avgFocus).setScale(1, RoundingMode.HALF_UP));

        overview.setStreakDays(calcStreak(records));

        // 目标统计
        List<StudyGoal> activeGoals = goalMapper.selectList(new LambdaQueryWrapper<StudyGoal>()
                .eq(StudyGoal::getUserId, userId)
                .eq(StudyGoal::getStatus, GoalStatus.IN_PROGRESS.getCode()));
        overview.setActiveGoalCount((long) activeGoals.size());
        if (!activeGoals.isEmpty()) {
            double avg = activeGoals.stream()
                    .mapToDouble(g -> g.getProgress() == null ? 0 : g.getProgress().doubleValue())
                    .average().orElse(0d);
            overview.setAvgGoalProgress(BigDecimal.valueOf(avg).setScale(1, RoundingMode.HALF_UP));
        }

        // 任务统计
        List<StudyTask> tasks = taskMapper.selectList(new LambdaQueryWrapper<StudyTask>()
                .eq(StudyTask::getUserId, userId));
        overview.setDoneTaskCount(tasks.stream().filter(t -> Objects.equals(t.getStatus(), TaskStatus.DONE.getCode())).count());
        overview.setTodoTaskCount(tasks.stream()
                .filter(t -> Objects.equals(t.getStatus(), TaskStatus.TODO.getCode())
                        || Objects.equals(t.getStatus(), TaskStatus.DOING.getCode())).count());
        overview.setOverdueTaskCount(tasks.stream()
                .filter(t -> t.getDueDate() != null
                        && t.getDueDate().isBefore(today)
                        && !Objects.equals(t.getStatus(), TaskStatus.DONE.getCode()))
                .count());

        // 时长趋势（补全连续日期）
        Map<LocalDate, List<StudyRecord>> byDate = records.stream()
                .filter(r -> r.getStudyDate() != null && !r.getStudyDate().isBefore(trendFrom))
                .collect(Collectors.groupingBy(StudyRecord::getStudyDate, LinkedHashMap::new, Collectors.toList()));
        vo.setTrend(DateUtils.range(trendFrom, today).stream().map(day -> {
            List<StudyRecord> dayRecords = byDate.getOrDefault(day, List.of());
            DashboardVO.TrendPoint point = new DashboardVO.TrendPoint();
            point.setDate(day);
            point.setMinutes(dayRecords.stream().mapToInt(r -> safe(r.getDurationMinutes())).sum());
            point.setRecords(dayRecords.size());
            double focus = dayRecords.stream()
                    .filter(r -> r.getFocusScore() != null && r.getFocusScore() > 0)
                    .mapToInt(StudyRecord::getFocusScore).average().orElse(0d);
            point.setFocus(BigDecimal.valueOf(focus).setScale(1, RoundingMode.HALF_UP));
            return point;
        }).toList());

        // 学科分布
        Map<String, List<StudyRecord>> bySubject = records.stream()
                .collect(Collectors.groupingBy(r -> safe(r.getSubject()), LinkedHashMap::new, Collectors.toList()));
        int finalTotalMinutes = totalMinutes;
        vo.setSubjects(bySubject.entrySet().stream()
                .sorted(Comparator.<Map.Entry<String, List<StudyRecord>>>comparingInt(
                        e -> e.getValue().stream().mapToInt(r -> safe(r.getDurationMinutes())).sum()).reversed())
                .limit(8)
                .map(e -> {
                    int minutes = e.getValue().stream().mapToInt(r -> safe(r.getDurationMinutes())).sum();
                    DashboardVO.SubjectSlice slice = new DashboardVO.SubjectSlice();
                    slice.setSubject(e.getKey());
                    slice.setMinutes(minutes);
                    slice.setRecords((long) e.getValue().size());
                    slice.setRatio(finalTotalMinutes == 0 ? BigDecimal.ZERO
                            : BigDecimal.valueOf(minutes * 100.0 / finalTotalMinutes).setScale(1, RoundingMode.HALF_UP));
                    return slice;
                }).toList());

        // 目标进度列表
        vo.setGoals(activeGoals.stream().map(g -> {
            DashboardVO.GoalPoint point = new DashboardVO.GoalPoint();
            point.setId(g.getId());
            point.setTitle(g.getTitle());
            point.setProgress(g.getProgress());
            point.setTargetHours(g.getTargetHours());
            point.setActualHours(g.getActualHours());
            point.setStatus(g.getStatus());
            point.setEndDate(g.getEndDate());
            return point;
        }).toList());

        // 待办 / 最近记录 / 最近 AI 结论 / 未读提醒
        vo.setUpcomingTasks(taskService.listUpcoming(userId, 6));
        List<RecordVO> recent = recordService.listRecent(userId, 5);
        vo.setRecentRecords(recent);

        AiTask latest = aiTaskMapper.selectOne(new LambdaQueryWrapper<AiTask>()
                .eq(AiTask::getUserId, userId)
                .eq(AiTask::getStatus, AiTaskStatus.SUCCEEDED.getCode())
                .orderByDesc(AiTask::getId)
                .last("LIMIT 1"));
        if (latest != null) {
            DashboardVO.LatestInsight insight = new DashboardVO.LatestInsight();
            insight.setTaskId(latest.getId());
            insight.setTaskNo(latest.getTaskNo());
            insight.setType(latest.getType());
            insight.setSummary(latest.getSummary());
            insight.setSuggestion(latest.getSuggestion());
            insight.setCreatedAt(latest.getCreatedAt() == null ? null : latest.getCreatedAt().toLocalDate());
            vo.setLatestInsight(insight);
        }

        Long unread = reminderMapper.selectCount(new LambdaQueryWrapper<com.witpath.entity.Reminder>()
                .eq(com.witpath.entity.Reminder::getUserId, userId)
                .eq(com.witpath.entity.Reminder::getStatus, 0));
        vo.setUnreadReminders(unread == null ? 0L : unread);

        if (user != null) {
            log.debug("构建概览完成：userId={} records={} goals={}", userId, records.size(), activeGoals.size());
        }
        return vo;
    }

    /**
     * 连续学习天数：从今天（若今天无记录则从昨天）向前累积
     */
    private int calcStreak(List<StudyRecord> records) {
        Map<LocalDate, Integer> minutesByDate = records.stream()
                .collect(Collectors.groupingBy(StudyRecord::getStudyDate,
                        Collectors.summingInt(r -> safe(r.getDurationMinutes()))));
        LocalDate cursor = LocalDate.now();
        if (!minutesByDate.containsKey(cursor)) {
            cursor = cursor.minusDays(1);
        }
        int streak = 0;
        while (minutesByDate.getOrDefault(cursor, 0) > 0) {
            streak++;
            cursor = cursor.minusDays(1);
        }
        return streak;
    }

    private static int safe(Integer value) {
        return value == null ? 0 : value;
    }

    private static String safe(String value) {
        return (value == null || value.isBlank()) ? "未分类" : value;
    }
}
