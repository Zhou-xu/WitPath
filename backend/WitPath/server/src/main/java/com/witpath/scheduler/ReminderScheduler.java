package com.witpath.scheduler;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.witpath.entity.User;
import com.witpath.mapper.UserMapper;
import com.witpath.service.ReminderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 定时任务：每日为近期活跃用户生成学习提醒
 * <p>
 * 只扫描 30 天内有登录记录的用户，避免全表扫描。
 * </p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ReminderScheduler {

    private final UserMapper userMapper;
    private final ReminderService reminderService;

    /**
     * 每天 07:30 执行
     */
    @Scheduled(cron = "0 30 7 * * *")
    public void generateDailyReminders() {
        LocalDateTime since = LocalDateTime.now().minusDays(30);
        List<User> users = userMapper.selectList(new LambdaQueryWrapper<User>()
                .select(User::getId)
                .ge(User::getLastLoginAt, since)
                .eq(User::getStatus, 1));

        int total = 0;
        for (User user : users) {
            try {
                total += reminderService.generateForUser(user.getId());
            } catch (Exception e) {
                log.warn("为用户生成提醒失败：userId={} msg={}", user.getId(), e.getMessage());
            }
        }
        log.info("每日提醒生成完成：活跃用户={} 新增提醒={}", users.size(), total);
    }
}
