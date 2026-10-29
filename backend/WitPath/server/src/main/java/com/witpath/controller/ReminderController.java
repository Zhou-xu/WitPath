package com.witpath.controller;

import com.witpath.context.UserContext;
import com.witpath.dto.PageQueryDTO;
import com.witpath.result.PageResult;
import com.witpath.result.Result;
import com.witpath.service.ReminderService;
import com.witpath.vo.ReminderVO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 提醒控制器
 */
@RestController
@RequestMapping("/api/reminders")
@RequiredArgsConstructor
public class ReminderController {

    private final ReminderService reminderService;

    @GetMapping("/page")
    public Result<PageResult<ReminderVO>> page(PageQueryDTO query,
                                               @RequestParam(value = "status", required = false) Integer status) {
        return Result.success(reminderService.page(UserContext.requireUserId(), query, status));
    }

    @GetMapping("/unread-count")
    public Result<Long> unreadCount() {
        return Result.success(reminderService.countUnread(UserContext.requireUserId()));
    }

    @PostMapping("/{id}/read")
    public Result<Boolean> read(@PathVariable Long id) {
        reminderService.markRead(UserContext.requireUserId(), id);
        return Result.success(true);
    }

    @PostMapping("/read-all")
    public Result<Integer> readAll() {
        return Result.success(reminderService.markAllRead(UserContext.requireUserId()));
    }

    @PostMapping("/generate")
    public Result<Integer> generate() {
        return Result.success(reminderService.generateForUser(UserContext.requireUserId()));
    }
}
