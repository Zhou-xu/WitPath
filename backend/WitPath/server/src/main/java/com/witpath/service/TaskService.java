package com.witpath.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.witpath.dto.PageQueryDTO;
import com.witpath.dto.TaskSaveDTO;
import com.witpath.entity.StudyRecord;
import com.witpath.entity.StudyTask;
import com.witpath.enumeration.TaskStatus;
import com.witpath.exception.BusinessException;
import com.witpath.mapper.StudyGoalMapper;
import com.witpath.mapper.StudyRecordMapper;
import com.witpath.mapper.StudyTaskMapper;
import com.witpath.result.PageResult;
import com.witpath.support.VoConverter;
import com.witpath.vo.TaskVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 学习任务业务服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TaskService {

    private final StudyTaskMapper taskMapper;
    private final StudyGoalMapper goalMapper;
    private final StudyRecordMapper recordMapper;
    private final GoalService goalService;

    /**
     * 任务分页列表
     *
     * @param status 任务状态，为空表示全部
     * @param goalId 目标 ID，为空表示不过滤
     */
    public PageResult<TaskVO> page(Long userId, PageQueryDTO query, Integer status, Long goalId) {
        Page<StudyTask> page = new Page<>(query.safePage(), query.safeSize());
        LambdaQueryWrapper<StudyTask> wrapper = new LambdaQueryWrapper<StudyTask>()
                .eq(StudyTask::getUserId, userId)
                .eq(status != null, StudyTask::getStatus, status)
                .eq(goalId != null, StudyTask::getGoalId, goalId)
                .and(StringUtils.hasText(query.getKeyword()), w -> w.like(StudyTask::getTitle, query.getKeyword()))
                .orderByAsc(StudyTask::getStatus)
                .orderByDesc(StudyTask::getPriority)
                .orderByAsc(StudyTask::getDueDate)
                .orderByDesc(StudyTask::getSortOrder);

        taskMapper.selectPage(page, wrapper);
        List<TaskVO> vos = toVOs(page.getRecords());
        return PageResult.of(page.getCurrent(), page.getSize(), page.getTotal(), vos);
    }

    /**
     * 首页待办：未完成且按截止日期升序，最多 limit 条
     */
    public List<TaskVO> listUpcoming(Long userId, int limit) {
        List<Integer> openStatus = List.of(TaskStatus.TODO.getCode(), TaskStatus.DOING.getCode(), TaskStatus.DELAYED.getCode());
        List<StudyTask> tasks = taskMapper.selectList(new LambdaQueryWrapper<StudyTask>()
                .eq(StudyTask::getUserId, userId)
                .in(StudyTask::getStatus, openStatus)
                .orderByAsc(StudyTask::getDueDate)
                .orderByDesc(StudyTask::getPriority)
                .last("LIMIT " + limit));
        return toVOs(tasks);
    }

    /**
     * 新增或修改任务
     */
    @Transactional(rollbackFor = Exception.class)
    public TaskVO save(Long userId, TaskSaveDTO dto) {
        if (!StringUtils.hasText(dto.getTitle())) {
            throw new BusinessException(400, "任务标题不能为空");
        }
        if (dto.getGoalId() != null) {
            Long count = goalMapper.selectCount(new LambdaQueryWrapper<com.witpath.entity.StudyGoal>()
                    .eq(com.witpath.entity.StudyGoal::getId, dto.getGoalId())
                    .eq(com.witpath.entity.StudyGoal::getUserId, userId));
            if (count == null || count == 0) {
                throw new BusinessException(404, "关联目标不存在");
            }
        }

        StudyTask task = new StudyTask();
        task.setId(dto.getId());
        task.setUserId(userId);
        task.setGoalId(dto.getGoalId());
        task.setTitle(dto.getTitle().trim());
        task.setDescription(dto.getDescription());
        task.setStatus(dto.getStatus() == null ? TaskStatus.TODO.getCode() : dto.getStatus());
        task.setPriority(dto.getPriority() == null ? 2 : dto.getPriority());
        task.setPlanDate(dto.getPlanDate());
        task.setDueDate(dto.getDueDate());
        task.setEstimatedMinutes(dto.getEstimatedMinutes() == null ? 0 : dto.getEstimatedMinutes());
        task.setSortOrder(dto.getSortOrder() == null ? 0 : dto.getSortOrder());

        if (dto.getId() == null) {
            task.setIsDeleted(0);
            taskMapper.insert(task);
        } else {
            getOwnedTask(userId, dto.getId());
            // 已完成时间随状态变化同步
            if (Objects.equals(task.getStatus(), TaskStatus.DONE.getCode())) {
                task.setCompletedAt(LocalDateTime.now());
            } else {
                task.setCompletedAt(null);
            }
            taskMapper.updateById(task);
        }
        // 目标上的任务计数与进度同步刷新
        if (task.getGoalId() != null) {
            recalcTaskActualMinutes(task.getGoalId(), task.getId());
            goalService.recalculate(userId, task.getGoalId());
        }
        return toVOs(List.of(taskMapper.selectById(task.getId()))).get(0);
    }

    /**
     * 切换任务状态
     */
    @Transactional(rollbackFor = Exception.class)
    public TaskVO changeStatus(Long userId, Long id, Integer status) {
        StudyTask task = getOwnedTask(userId, id);
        TaskStatus target = TaskStatus.of(status);

        StudyTask update = new StudyTask();
        update.setId(id);
        update.setStatus(target.getCode());
        if (target == TaskStatus.DONE) {
            update.setCompletedAt(LocalDateTime.now());
        } else {
            update.setCompletedAt(null);
        }
        taskMapper.updateById(update);

        // 同步目标上的任务计数与进度
        if (task.getGoalId() != null) {
            recalcTaskActualMinutes(task.getGoalId(), task.getId());
            goalService.recalculate(userId, task.getGoalId());
        }
        return toVOs(List.of(taskMapper.selectById(id))).get(0);
    }

    /**
     * 删除任务
     */
    @Transactional(rollbackFor = Exception.class)
    public void remove(Long userId, Long id) {
        StudyTask task = getOwnedTask(userId, id);
        taskMapper.deleteById(id);
        if (task.getGoalId() != null) {
            goalService.recalculate(userId, task.getGoalId());
            log.info("删除任务：taskId={} goalId={}", id, task.getGoalId());
        }
    }

    /**
     * 根据已关联的学习记录回填任务实际耗时
     */
    @Transactional(rollbackFor = Exception.class)
    public void recalcTaskActualMinutes(Long goalId, Long taskId) {
        if (taskId == null) {
            return;
        }
        List<StudyRecord> records = recordMapper.selectList(new LambdaQueryWrapper<StudyRecord>()
                .select(StudyRecord::getDurationMinutes)
                .eq(StudyRecord::getTaskId, taskId));
        int minutes = records.stream()
                .map(r -> r.getDurationMinutes() == null ? 0 : r.getDurationMinutes())
                .mapToInt(Integer::intValue).sum();

        StudyTask update = new StudyTask();
        update.setId(taskId);
        update.setActualMinutes(minutes);
        taskMapper.updateById(update);
    }

    /**
     * 今日到期 / 逾期的未完成任务，用于生成提醒
     */
    public List<StudyTask> listDueTasks(Long userId, LocalDate today) {
        List<Integer> openStatus = List.of(TaskStatus.TODO.getCode(), TaskStatus.DOING.getCode());
        return taskMapper.selectList(new LambdaQueryWrapper<StudyTask>()
                .eq(StudyTask::getUserId, userId)
                .in(StudyTask::getStatus, openStatus)
                .le(StudyTask::getDueDate, today.plusDays(1)));
    }

    public StudyTask getOwnedTask(Long userId, Long id) {
        StudyTask task = taskMapper.selectById(id);
        if (task == null || !Objects.equals(task.getUserId(), userId)) {
            throw new BusinessException(404, "任务不存在或无权访问");
        }
        return task;
    }

    /**
     * 批量填充目标标题后转换为 VO
     */
    private List<TaskVO> toVOs(List<StudyTask> tasks) {
        if (tasks.isEmpty()) {
            return List.of();
        }
        Set<Long> goalIds = tasks.stream().map(StudyTask::getGoalId)
                .filter(Objects::nonNull).collect(Collectors.toSet());
        // 同样避免不可变 Map 在 get(null) 时抛 NPE
        Map<Long, String> goalTitles = goalIds.isEmpty() ? new HashMap<>() :
                goalMapper.selectBatchIds(goalIds).stream()
                        .collect(Collectors.toMap(com.witpath.entity.StudyGoal::getId,
                                com.witpath.entity.StudyGoal::getTitle,
                                (a, b) -> a));
        return tasks.stream()
                .map(t -> VoConverter.toTaskVO(t, goalTitles.get(t.getGoalId())))
                .toList();
    }
}
