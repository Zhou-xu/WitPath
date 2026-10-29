package com.witpath.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.witpath.constant.Constants;
import com.witpath.dto.PageQueryDTO;
import com.witpath.dto.RecordSaveDTO;
import com.witpath.entity.StudyGoal;
import com.witpath.entity.StudyRecord;
import com.witpath.entity.StudyTask;
import com.witpath.exception.BusinessException;
import com.witpath.mapper.StudyGoalMapper;
import com.witpath.mapper.StudyRecordMapper;
import com.witpath.mapper.StudyTaskMapper;
import com.witpath.result.PageResult;
import com.witpath.support.VoConverter;
import com.witpath.utils.DateUtils;
import com.witpath.vo.RecordVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RedissonClient;
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
import java.util.stream.Collectors;

/**
 * 学习记录业务服务
 * <p>
 * 每次写入 / 更新 / 删除都会同步刷新所属目标与任务的汇总字段，并失效概览缓存。
 * </p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RecordService {

    private final StudyRecordMapper recordMapper;
    private final StudyGoalMapper goalMapper;
    private final StudyTaskMapper taskMapper;
    private final GoalService goalService;
    private final TaskService taskService;
    private final RedissonClient redissonClient;

    /**
     * 学习记录分页列表
     */
    public PageResult<RecordVO> page(Long userId, PageQueryDTO query, LocalDate from, LocalDate to, String subject) {
        Page<StudyRecord> page = new Page<>(query.safePage(), query.safeSize());
        LambdaQueryWrapper<StudyRecord> wrapper = new LambdaQueryWrapper<StudyRecord>()
                .eq(StudyRecord::getUserId, userId)
                .ge(from != null, StudyRecord::getStudyDate, from)
                .le(to != null, StudyRecord::getStudyDate, to)
                .eq(StringUtils.hasText(subject), StudyRecord::getSubject, subject)
                .and(StringUtils.hasText(query.getKeyword()), w -> w
                        .like(StudyRecord::getTitle, query.getKeyword())
                        .or().like(StudyRecord::getContent, query.getKeyword()))
                .orderByDesc(StudyRecord::getStudyDate)
                .orderByDesc(StudyRecord::getId);

        recordMapper.selectPage(page, wrapper);
        List<RecordVO> vos = toVOs(page.getRecords());
        return PageResult.of(page.getCurrent(), page.getSize(), page.getTotal(), vos);
    }

    /**
     * 最近学习记录
     */
    public List<RecordVO> listRecent(Long userId, int limit) {
        List<StudyRecord> records = recordMapper.selectList(new LambdaQueryWrapper<StudyRecord>()
                .eq(StudyRecord::getUserId, userId)
                .orderByDesc(StudyRecord::getStudyDate)
                .orderByDesc(StudyRecord::getId)
                .last("LIMIT " + limit));
        return toVOs(records);
    }

    /**
     * 指定日期范围内的学习记录，用于 AI 上下文与统计
     */
    public List<StudyRecord> listInRange(Long userId, LocalDate from, LocalDate to, int limit) {
        LambdaQueryWrapper<StudyRecord> wrapper = new LambdaQueryWrapper<StudyRecord>()
                .eq(StudyRecord::getUserId, userId)
                .ge(from != null, StudyRecord::getStudyDate, from)
                .le(to != null, StudyRecord::getStudyDate, to)
                .orderByDesc(StudyRecord::getStudyDate)
                .orderByDesc(StudyRecord::getId);
        if (limit > 0) {
            wrapper.last("LIMIT " + limit);
        }
        return recordMapper.selectList(wrapper);
    }

    /**
     * 新增或修改学习记录
     */
    @Transactional(rollbackFor = Exception.class)
    public RecordVO save(Long userId, RecordSaveDTO dto) {
        if (!StringUtils.hasText(dto.getTitle())) {
            throw new BusinessException(400, "学习标题不能为空");
        }

        LocalDate studyDate = dto.getStudyDate() != null ? dto.getStudyDate() : LocalDate.now();
        int minutes = dto.getDurationMinutes() != null && dto.getDurationMinutes() > 0
                ? dto.getDurationMinutes()
                : DateUtils.durationMinutes(dto.getStartAt(), dto.getEndAt());
        if (minutes <= 0) {
            throw new BusinessException(400, "请填写学习时长，或填写合法的开始与结束时间");
        }

        // 校验目标 / 任务归属
        if (dto.getGoalId() != null) {
            Long count = goalMapper.selectCount(new LambdaQueryWrapper<StudyGoal>()
                    .eq(StudyGoal::getId, dto.getGoalId()).eq(StudyGoal::getUserId, userId));
            if (count == null || count == 0) {
                throw new BusinessException(404, "关联目标不存在");
            }
        }
        if (dto.getTaskId() != null) {
            Long count = taskMapper.selectCount(new LambdaQueryWrapper<StudyTask>()
                    .eq(StudyTask::getId, dto.getTaskId()).eq(StudyTask::getUserId, userId));
            if (count == null || count == 0) {
                throw new BusinessException(404, "关联任务不存在");
            }
        }

        StudyRecord record = new StudyRecord();
        record.setId(dto.getId());
        record.setUserId(userId);
        record.setGoalId(dto.getGoalId());
        record.setTaskId(dto.getTaskId());
        record.setTitle(dto.getTitle().trim());
        record.setContent(dto.getContent());
        record.setSubject(dto.getSubject());
        record.setStudyDate(studyDate);
        record.setStartAt(dto.getStartAt());
        record.setEndAt(dto.getEndAt());
        record.setDurationMinutes(minutes);
        record.setFocusScore(dto.getFocusScore());
        record.setOutcome(dto.getOutcome());
        record.setTags(dto.getTags());

        if (dto.getId() == null) {
            record.setIsDeleted(0);
            recordMapper.insert(record);
        } else {
            StudyRecord old = getOwnedRecord(userId, dto.getId());
            // 处理之前挂在不同目标/任务上的情况
            recordMapper.updateById(record);
            syncAggregates(userId, old.getGoalId(), old.getTaskId());
        }

        syncAggregates(userId, record.getGoalId(), record.getTaskId());
        evictDashboardCache(userId);
        return toVOs(List.of(recordMapper.selectById(record.getId()))).get(0);
    }

    /**
     * 删除学习记录
     */
    @Transactional(rollbackFor = Exception.class)
    public void remove(Long userId, Long id) {
        StudyRecord record = getOwnedRecord(userId, id);
        recordMapper.deleteById(id);
        syncAggregates(userId, record.getGoalId(), record.getTaskId());
        evictDashboardCache(userId);
    }

    public StudyRecord getOwnedRecord(Long userId, Long id) {
        StudyRecord record = recordMapper.selectById(id);
        if (record == null || !Objects.equals(record.getUserId(), userId)) {
            throw new BusinessException(404, "学习记录不存在或无权访问");
        }
        return record;
    }

    /**
     * 失效概览缓存
     */
    public void evictDashboardCache(Long userId) {
        try {
            redissonClient.getBucket(Constants.REDIS_DASHBOARD_KEY + userId).delete();
        } catch (Exception e) {
            log.warn("清除概览缓存失败：{}", e.getMessage());
        }
    }

    /**
     * 同步目标统计与任务实际耗时
     */
    private void syncAggregates(Long userId, Long goalId, Long taskId) {
        if (taskId != null) {
            taskService.recalcTaskActualMinutes(goalId, taskId);
        }
        if (goalId != null) {
            goalService.recalculate(userId, goalId);
        }
    }

    private List<RecordVO> toVOs(List<StudyRecord> records) {
        if (records.isEmpty()) {
            return List.of();
        }
        Map<Long, String> goalTitles = loadGoalTitles(records);
        Map<Long, String> taskTitles = loadTaskTitles(records);
        return records.stream()
                .map(r -> VoConverter.toRecordVO(r, goalTitles.get(r.getGoalId()), taskTitles.get(r.getTaskId())))
                .toList();
    }

    private Map<Long, String> loadGoalTitles(List<StudyRecord> records) {
        Set<Long> ids = records.stream().map(StudyRecord::getGoalId).filter(Objects::nonNull).collect(Collectors.toSet());
        if (ids.isEmpty()) {
            // 必须用可变 HashMap：Map.of() 返回的不可变 Map 在 get(null) 时会抛 NPE
            return new HashMap<>();
        }
        return goalMapper.selectBatchIds(ids).stream()
                .collect(Collectors.toMap(StudyGoal::getId, StudyGoal::getTitle, (a, b) -> a));
    }

    private Map<Long, String> loadTaskTitles(List<StudyRecord> records) {
        Set<Long> ids = records.stream().map(StudyRecord::getTaskId).filter(Objects::nonNull).collect(Collectors.toSet());
        if (ids.isEmpty()) {
            return new HashMap<>();
        }
        return taskMapper.selectBatchIds(ids).stream()
                .collect(Collectors.toMap(StudyTask::getId, StudyTask::getTitle, (a, b) -> a));
    }
}
