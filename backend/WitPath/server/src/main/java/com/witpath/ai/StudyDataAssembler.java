package com.witpath.ai;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.witpath.constant.Constants;
import com.witpath.dto.AiContextPayload;
import com.witpath.entity.AiTask;
import com.witpath.entity.StudyGoal;
import com.witpath.entity.StudyMaterial;
import com.witpath.entity.StudyRecord;
import com.witpath.entity.StudyTask;
import com.witpath.entity.User;
import com.witpath.enumeration.TaskStatus;
import com.witpath.mapper.StudyGoalMapper;
import com.witpath.mapper.StudyMaterialMapper;
import com.witpath.mapper.StudyRecordMapper;
import com.witpath.mapper.StudyTaskMapper;
import com.witpath.mapper.UserMapper;
import com.witpath.support.VoConverter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

/**
 * 学习数据装配器：把用户在 MySQL 中的业务数据整理成 AI 服务可消费的上下文载荷
 * <p>
 * 职责边界：只做数据组装与权限过滤（仅限当前用户数据），不做任何分析推理。
 * </p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class StudyDataAssembler {

    private final UserMapper userMapper;
    private final StudyGoalMapper goalMapper;
    private final StudyTaskMapper taskMapper;
    private final StudyRecordMapper recordMapper;
    private final StudyMaterialMapper materialMapper;

    /**
     * 组装 AI 上下文
     *
     * @param userId     用户 ID
     * @param taskNo     任务编号
     * @param type       任务类型
     * @param question   用户补充说明
     * @param rangeDays  学习数据窗口天数
     * @param focusGoalId 聚焦的目标，为空表示分析全部进行中目标
     */
    public AiContextPayload assemble(Long userId, String taskNo, String type,
                                     String question, Integer rangeDays, Long focusGoalId,
                                     String callbackUrl, String callbackToken) {
        int days = (rangeDays == null || rangeDays <= 0) ? 14 : Math.min(rangeDays, 120);
        LocalDate to = LocalDate.now();
        LocalDate from = to.minusDays(days - 1L);

        AiContextPayload payload = new AiContextPayload();
        payload.setUserId(userId);
        payload.setTaskNo(taskNo);
        payload.setTaskType(type);
        payload.setQuestion(question);
        payload.setCallbackUrl(callbackUrl);
        payload.setCallbackToken(callbackToken);

        User user = userMapper.selectById(userId);
        if (user != null) {
            AiContextPayload.UserProfile profile = new AiContextPayload.UserProfile();
            profile.setNickname(user.getNickname());
            profile.setStudyStage(user.getStudyStage());
            profile.setStudyGoal(user.getStudyGoal());
            profile.setPreferredTags(VoConverter.splitTags(user.getPreferredTags()));
            payload.setProfile(profile);
        }

        // 目标
        List<StudyGoal> goals = goalMapper.selectList(new LambdaQueryWrapper<StudyGoal>()
                .eq(StudyGoal::getUserId, userId)
                .eq(focusGoalId != null, StudyGoal::getId, focusGoalId)
                .orderByDesc(StudyGoal::getPriority)
                .orderByAsc(StudyGoal::getEndDate)
                .last("LIMIT " + Constants.AI_CONTEXT_MAX_GOALS));
        payload.setGoals(goals.stream().map(g -> {
            AiContextPayload.GoalItem item = new AiContextPayload.GoalItem();
            item.setId(g.getId());
            item.setTitle(g.getTitle());
            item.setSubject(g.getSubject());
            item.setStatus(g.getStatus());
            item.setStartDate(g.getStartDate());
            item.setEndDate(g.getEndDate());
            item.setTargetHours(nz(g.getTargetHours()));
            item.setActualHours(nz(g.getActualHours()));
            item.setProgress(nz(g.getProgress()));
            item.setTaskTotal(g.getTaskTotal());
            item.setTaskDone(g.getTaskDone());
            return item;
        }).toList());

        // 任务
        List<StudyTask> tasks = taskMapper.selectList(new LambdaQueryWrapper<StudyTask>()
                .eq(StudyTask::getUserId, userId)
                .orderByAsc(StudyTask::getDueDate)
                .last("LIMIT 100"));
        payload.setTasks(tasks.stream().map(t -> {
            AiContextPayload.TaskItem item = new AiContextPayload.TaskItem();
            item.setId(t.getId());
            item.setGoalId(t.getGoalId());
            item.setTitle(t.getTitle());
            item.setStatus(t.getStatus());
            item.setPriority(t.getPriority());
            item.setDueDate(t.getDueDate());
            item.setEstimatedMinutes(t.getEstimatedMinutes());
            boolean done = Objects.equals(t.getStatus(), TaskStatus.DONE.getCode());
            item.setOverdue(!done && t.getDueDate() != null && t.getDueDate().isBefore(to));
            return item;
        }).toList());

        // 学习记录（窗口内，按条数上限保护）
        List<StudyRecord> records = recordMapper.selectList(new LambdaQueryWrapper<StudyRecord>()
                .select(StudyRecord::getStudyDate, StudyRecord::getTitle, StudyRecord::getSubject,
                        StudyRecord::getDurationMinutes, StudyRecord::getFocusScore, StudyRecord::getOutcome)
                .eq(StudyRecord::getUserId, userId)
                .ge(StudyRecord::getStudyDate, from)
                .orderByDesc(StudyRecord::getStudyDate)
                .last("LIMIT " + Constants.AI_CONTEXT_MAX_RECORDS));
        payload.setRecords(records.stream().map(r -> {
            AiContextPayload.RecordItem item = new AiContextPayload.RecordItem();
            item.setStudyDate(r.getStudyDate());
            item.setTitle(r.getTitle());
            item.setSubject(r.getSubject());
            item.setDurationMinutes(r.getDurationMinutes());
            item.setFocusScore(r.getFocusScore());
            item.setOutcome(r.getOutcome());
            return item;
        }).toList());

        int totalMinutes = records.stream().mapToInt(r -> r.getDurationMinutes() == null ? 0 : r.getDurationMinutes()).sum();
        double avgFocus = records.stream()
                .filter(r -> r.getFocusScore() != null && r.getFocusScore() > 0)
                .mapToInt(StudyRecord::getFocusScore).average().orElse(0d);

        AiContextPayload.Summary summary = new AiContextPayload.Summary();
        summary.setRangeDays(days);
        summary.setTotalMinutes(totalMinutes);
        summary.setRecordCount(records.size());
        summary.setAvgFocus(BigDecimal.valueOf(avgFocus).setScale(1, RoundingMode.HALF_UP));
        summary.setFrom(from);
        summary.setTo(to);
        payload.setSummary(summary);

        // 资料清单（AI 侧据此决定是否走知识检索）
        List<StudyMaterial> materials = materialMapper.selectList(new LambdaQueryWrapper<StudyMaterial>()
                .eq(StudyMaterial::getUserId, userId)
                .orderByDesc(StudyMaterial::getId)
                .last("LIMIT 20"));
        payload.setMaterialSnippets(materials.stream().map(m -> m.getName()
                + (m.getSubject() == null ? "" : "(" + m.getSubject() + ")")).toList());

        log.debug("AI 上下文装配完成：userId={} goals={} tasks={} records={}", userId, goals.size(), tasks.size(), records.size());
        return payload;
    }

    private static BigDecimal nz(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}
