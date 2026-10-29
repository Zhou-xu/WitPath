package com.witpath.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.witpath.dto.PageQueryDTO;
import com.witpath.entity.Reminder;
import com.witpath.enumeration.ReminderStatus;
import com.witpath.exception.BusinessException;
import com.witpath.mapper.ReminderMapper;
import com.witpath.result.PageResult;
import com.witpath.support.VoConverter;
import com.witpath.vo.ReminderVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

/**
 * 提醒服务：任务到期、目标临期、AI 建议等业务的读写与生成
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReminderService {

    private final ReminderMapper reminderMapper;
    private final TaskService taskService;
    private final GoalService goalService;

    public PageResult<ReminderVO> page(Long userId, PageQueryDTO query, Integer status) {
        Page<Reminder> page = new Page<>(query.safePage(), query.safeSize());
        LambdaQueryWrapper<Reminder> wrapper = new LambdaQueryWrapper<Reminder>()
                .eq(Reminder::getUserId, userId)
                .eq(status != null, Reminder::getStatus, status)
                .orderByAsc(Reminder::getStatus)
                .orderByDesc(Reminder::getCreatedAt);
        reminderMapper.selectPage(page, wrapper);

        List<ReminderVO> vos = page.getRecords().stream().map(VoConverter::toReminderVO).toList();
        return PageResult.of(page.getCurrent(), page.getSize(), page.getTotal(), vos);
    }

    @Transactional(rollbackFor = Exception.class)
    public void markRead(Long userId, Long id) {
        Reminder reminder = reminderMapper.selectById(id);
        if (reminder == null || !Objects.equals(reminder.getUserId(), userId)) {
            throw new BusinessException(404, "提醒不存在或无权访问");
        }
        Reminder update = new Reminder();
        update.setId(id);
        update.setStatus(ReminderStatus.READ.getCode());
        update.setReadAt(LocalDateTime.now());
        reminderMapper.updateById(update);
    }

    @Transactional(rollbackFor = Exception.class)
    public int markAllRead(Long userId) {
        Reminder update = new Reminder();
        update.setStatus(ReminderStatus.READ.getCode());
        update.setReadAt(LocalDateTime.now());
        return reminderMapper.update(update, new LambdaQueryWrapper<Reminder>()
                .eq(Reminder::getUserId, userId)
                .eq(Reminder::getStatus, ReminderStatus.UNREAD.getCode()));
    }

    public long countUnread(Long userId) {
        Long count = reminderMapper.selectCount(new LambdaQueryWrapper<Reminder>()
                .eq(Reminder::getUserId, userId)
                .eq(Reminder::getStatus, ReminderStatus.UNREAD.getCode()));
        return count == null ? 0L : count;
    }

    /**
     * 为单个用户生成当日提醒（任务到期 / 目标临期）
     * <p>
     * 幂等保证：同一用户 + 同一类型 + 同一关联对象当天只生成一条。
     * </p>
     *
     * @return 新生成的提醒条数
     */
    @Transactional(rollbackFor = Exception.class)
    public int generateForUser(Long userId) {
        LocalDate today = LocalDate.now();
        LocalDateTime dayStart = today.atStartOfDay();
        int created = 0;

        for (com.witpath.entity.StudyTask task : taskService.listDueTasks(userId, today)) {
            if (existsToday(userId, "TASK_DUE", task.getId(), dayStart)) {
                continue;
            }
            long daysLeft = task.getDueDate() == null ? 0
                    : java.time.temporal.ChronoUnit.DAYS.between(today, task.getDueDate());
            Reminder reminder = new Reminder();
            reminder.setUserId(userId);
            reminder.setTaskId(task.getId());
            reminder.setType("TASK_DUE");
            reminder.setLevel(daysLeft < 0 ? 3 : (daysLeft <= 1 ? 2 : 1));
            reminder.setTitle(daysLeft < 0 ? "任务已逾期" : "任务即将到期");
            reminder.setContent(task.getTitle() + "（" + (daysLeft < 0 ? "已逾期 " + (-daysLeft) + " 天" : "剩余 " + daysLeft + " 天") + "）");
            reminder.setSource("SYSTEM");
            reminder.setStatus(ReminderStatus.UNREAD.getCode());
            reminder.setTriggerTime(LocalDateTime.now());
            reminder.setIsDeleted(0);
            reminderMapper.insert(reminder);
            created++;
        }

        for (com.witpath.entity.StudyGoal goal : goalService.listDueSoon(userId, today)) {
            if (existsToday(userId, "GOAL_DEADLINE", goal.getId(), dayStart)) {
                continue;
            }
            long daysLeft = goal.getEndDate() == null ? 0
                    : java.time.temporal.ChronoUnit.DAYS.between(today, goal.getEndDate());
            Reminder reminder = new Reminder();
            reminder.setUserId(userId);
            reminder.setGoalId(goal.getId());
            reminder.setType("GOAL_DEADLINE");
            reminder.setLevel(daysLeft <= 1 ? 3 : 2);
            reminder.setTitle("目标即将到期");
            reminder.setContent(goal.getTitle() + "（剩余 " + daysLeft + " 天，当前进度 "
                    + (goal.getProgress() == null ? 0 : goal.getProgress().intValue()) + "%）");
            reminder.setSource("SYSTEM");
            reminder.setStatus(ReminderStatus.UNREAD.getCode());
            reminder.setTriggerTime(LocalDateTime.now());
            reminder.setIsDeleted(0);
            reminderMapper.insert(reminder);
            created++;
        }

        if (created > 0) {
            log.info("为用户生成提醒：userId={} count={}", userId, created);
        }
        return created;
    }

    private boolean existsToday(Long userId, String type, Long refId, LocalDateTime dayStart) {
        LambdaQueryWrapper<Reminder> wrapper = new LambdaQueryWrapper<Reminder>()
                .eq(Reminder::getUserId, userId)
                .eq(Reminder::getType, type)
                .ge(Reminder::getCreatedAt, dayStart);
        if ("TASK_DUE".equals(type)) {
            wrapper.eq(Reminder::getTaskId, refId);
        } else {
            wrapper.eq(Reminder::getGoalId, refId);
        }
        Long count = reminderMapper.selectCount(wrapper);
        return count != null && count > 0;
    }
}
