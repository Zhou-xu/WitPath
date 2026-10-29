package com.witpath.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.witpath.context.UserContext;
import com.witpath.dto.GoalSaveDTO;
import com.witpath.dto.PageQueryDTO;
import com.witpath.entity.StudyGoal;
import com.witpath.entity.StudyRecord;
import com.witpath.entity.StudyTask;
import com.witpath.enumeration.GoalStatus;
import com.witpath.enumeration.TaskStatus;
import com.witpath.exception.BusinessException;
import com.witpath.mapper.StudyGoalMapper;
import com.witpath.mapper.StudyRecordMapper;
import com.witpath.mapper.StudyTaskMapper;
import com.witpath.result.PageResult;
import com.witpath.support.VoConverter;
import com.witpath.vo.GoalVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

/**
 * 学习目标业务服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GoalService {

    private final StudyGoalMapper goalMapper;
    private final StudyTaskMapper taskMapper;
    private final StudyRecordMapper recordMapper;

    /**
     * 目标分页列表（支持按状态、关键字过滤）
     */
    public PageResult<GoalVO> page(Long userId, PageQueryDTO query, Integer status) {
        Page<StudyGoal> page = new Page<>(query.safePage(), query.safeSize());
        LambdaQueryWrapper<StudyGoal> wrapper = new LambdaQueryWrapper<StudyGoal>()
                .eq(StudyGoal::getUserId, userId)
                .eq(status != null, StudyGoal::getStatus, status)
                .and(StringUtils.hasText(query.getKeyword()), w -> w
                        .like(StudyGoal::getTitle, query.getKeyword())
                        .or().like(StudyGoal::getSubject, query.getKeyword()))
                .orderByAsc(StudyGoal::getStatus)
                .orderByDesc(StudyGoal::getPriority)
                .orderByDesc(StudyGoal::getId);

        goalMapper.selectPage(page, wrapper);
        List<GoalVO> vos = page.getRecords().stream().map(VoConverter::toGoalVO).toList();
        return PageResult.of(page.getCurrent(), page.getSize(), page.getTotal(), vos);
    }

    /**
     * 目标详情（含所属任务列表）
     */
    public GoalVO detail(Long userId, Long id) {
        StudyGoal goal = getOwnedGoal(userId, id);
        return VoConverter.toGoalVO(goal);
    }

    /**
     * 新增或修改目标
     */
    @Transactional(rollbackFor = Exception.class)
    public GoalVO save(Long userId, GoalSaveDTO dto) {
        if (!StringUtils.hasText(dto.getTitle())) {
            throw new BusinessException(400, "目标标题不能为空");
        }
        if (dto.getStartDate() != null && dto.getEndDate() != null
                && dto.getEndDate().isBefore(dto.getStartDate())) {
            throw new BusinessException(400, "截止日期不能早于开始日期");
        }

        StudyGoal goal = new StudyGoal();
        goal.setId(dto.getId());
        goal.setUserId(userId);
        goal.setTitle(dto.getTitle().trim());
        goal.setDescription(dto.getDescription());
        goal.setSubject(dto.getSubject());
        goal.setPriority(dto.getPriority() == null ? 2 : dto.getPriority());
        goal.setStatus(dto.getStatus() == null ? GoalStatus.IN_PROGRESS.getCode() : dto.getStatus());
        goal.setStartDate(dto.getStartDate());
        goal.setEndDate(dto.getEndDate());
        goal.setTargetHours(dto.getTargetHours() == null ? BigDecimal.ZERO : dto.getTargetHours());

        if (dto.getId() == null) {
            goal.setActualHours(BigDecimal.ZERO);
            goal.setTaskTotal(0);
            goal.setTaskDone(0);
            goal.setProgress(BigDecimal.ZERO);
            goal.setIsDeleted(0);
            goalMapper.insert(goal);
        } else {
            getOwnedGoal(userId, dto.getId());
            goalMapper.updateById(goal);
        }

        recalculate(userId, goal.getId());
        return VoConverter.toGoalVO(goalMapper.selectById(goal.getId()));
    }

    /**
     * 逻辑删除目标：同时归档其未完成任务的归属关系（任务本身一并逻辑删除）
     */
    @Transactional(rollbackFor = Exception.class)
    public void remove(Long userId, Long id) {
        getOwnedGoal(userId, id);
        LocalDateTime now = LocalDateTime.now();

        taskMapper.delete(new LambdaQueryWrapper<StudyTask>().eq(StudyTask::getGoalId, id));
        goalMapper.deleteById(id);
        log.info("删除目标：goalId={} userId={}，同时清理关联任务 {}", id, userId, now != null ? "" : "");
    }

    /**
     * 切换目标状态（完成 / 暂停 / 恢复进行中）
     */
    @Transactional(rollbackFor = Exception.class)
    public GoalVO changeStatus(Long userId, Long id, Integer status) {
        StudyGoal goal = getOwnedGoal(userId, id);
        GoalStatus target = GoalStatus.of(status);
        StudyGoal update = new StudyGoal();
        update.setId(id);
        update.setStatus(target.getCode());
        goalMapper.updateById(update);
        recalculate(userId, id);
        return VoConverter.toGoalVO(goalMapper.selectById(id));
    }

    /**
     * 重新计算目标的汇总字段
     * <p>
     * 进度口径：存在任务时按任务完成率计算，否则按 实际小时 / 计划小时 计算，两者都没有则保持 0。
     * </p>
     */
    @Transactional(rollbackFor = Exception.class)
    public void recalculate(Long userId, Long goalId) {
        if (goalId == null) {
            return;
        }
        StudyGoal goal = goalMapper.selectOne(new LambdaQueryWrapper<StudyGoal>()
                .eq(StudyGoal::getId, goalId)
                .eq(StudyGoal::getUserId, userId));
        if (goal == null) {
            return;
        }

        Long taskTotal = taskMapper.selectCount(new LambdaQueryWrapper<StudyTask>()
                .eq(StudyTask::getGoalId, goalId));
        Long taskDone = taskMapper.selectCount(new LambdaQueryWrapper<StudyTask>()
                .eq(StudyTask::getGoalId, goalId)
                .eq(StudyTask::getStatus, TaskStatus.DONE.getCode()));

        // 累计该目标下的学习时长
        List<StudyRecord> records = recordMapper.selectList(new LambdaQueryWrapper<StudyRecord>()
                .select(StudyRecord::getDurationMinutes)
                .eq(StudyRecord::getGoalId, goalId));
        int totalMinutes = records.stream()
                .map(r -> r.getDurationMinutes() == null ? 0 : r.getDurationMinutes())
                .mapToInt(Integer::intValue)
                .sum();

        BigDecimal progress;
        if (taskTotal > 0) {
            progress = BigDecimal.valueOf(taskDone * 100.0 / taskTotal);
        } else if (goal.getTargetHours() != null && goal.getTargetHours().compareTo(BigDecimal.ZERO) > 0) {
            progress = BigDecimal.valueOf(totalMinutes / 60.0 * 100.0)
                    .divide(goal.getTargetHours(), 2, RoundingMode.HALF_UP);
        } else {
            progress = BigDecimal.ZERO;
        }
        // 上限 100%
        if (progress.compareTo(BigDecimal.valueOf(100)) > 0) {
            progress = BigDecimal.valueOf(100);
        }

        StudyGoal update = new StudyGoal();
        update.setId(goalId);
        update.setTaskTotal(taskTotal.intValue());
        update.setTaskDone(taskDone.intValue());
        update.setActualHours(BigDecimal.valueOf(totalMinutes / 60.0).setScale(2, RoundingMode.HALF_UP));
        update.setProgress(progress.setScale(2, RoundingMode.HALF_UP));

        // 任务全部完成时自动标记目标完成
        if (taskTotal > 0 && Objects.equals(taskTotal, taskDone)
                && !Objects.equals(goal.getStatus(), GoalStatus.COMPLETED.getCode())) {
            update.setStatus(GoalStatus.COMPLETED.getCode());
        }
        goalMapper.updateById(update);
    }

    /**
     * 校验目标归属
     */
    public StudyGoal getOwnedGoal(Long userId, Long id) {
        StudyGoal goal = goalMapper.selectById(id);
        if (goal == null || !Objects.equals(goal.getUserId(), userId)) {
            throw new BusinessException(404, "目标不存在或无权访问");
        }
        return goal;
    }

    /**
     * 查询用户进行中的目标（供 AI 上下文与提醒使用）
     */
    public List<StudyGoal> listActive(Long userId) {
        return goalMapper.selectList(new LambdaQueryWrapper<StudyGoal>()
                .eq(StudyGoal::getUserId, userId)
                .eq(StudyGoal::getStatus, GoalStatus.IN_PROGRESS.getCode())
                .orderByDesc(StudyGoal::getPriority)
                .orderByAsc(StudyGoal::getEndDate));
    }

    /**
     * 今日应提醒的临期目标（3 天内到期且未完成）
     */
    public List<StudyGoal> listDueSoon(Long userId, LocalDate today) {
        return goalMapper.selectList(new LambdaQueryWrapper<StudyGoal>()
                .eq(StudyGoal::getUserId, userId)
                .ne(StudyGoal::getStatus, GoalStatus.COMPLETED.getCode())
                .ne(StudyGoal::getStatus, GoalStatus.ARCHIVED.getCode())
                .le(StudyGoal::getEndDate, today.plusDays(3))
                .ge(StudyGoal::getEndDate, today));
    }
}
