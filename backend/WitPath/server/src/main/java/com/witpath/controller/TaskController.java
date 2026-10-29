package com.witpath.controller;

import com.witpath.context.UserContext;
import com.witpath.dto.PageQueryDTO;
import com.witpath.dto.TaskSaveDTO;
import com.witpath.result.PageResult;
import com.witpath.result.Result;
import com.witpath.service.TaskService;
import com.witpath.vo.TaskVO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 学习任务控制器
 */
@RestController
@RequestMapping("/api/tasks")
@RequiredArgsConstructor
public class TaskController {

    private final TaskService taskService;

    @GetMapping("/page")
    public Result<PageResult<TaskVO>> page(PageQueryDTO query,
                                           @RequestParam(value = "status", required = false) Integer status,
                                           @RequestParam(value = "goalId", required = false) Long goalId) {
        return Result.success(taskService.page(UserContext.requireUserId(), query, status, goalId));
    }

    @GetMapping("/upcoming")
    public Result<List<TaskVO>> upcoming(@RequestParam(value = "limit", defaultValue = "6") Integer limit) {
        return Result.success(taskService.listUpcoming(UserContext.requireUserId(), limit));
    }

    @PostMapping
    public Result<TaskVO> save(@RequestBody TaskSaveDTO dto) {
        return Result.success(taskService.save(UserContext.requireUserId(), dto));
    }

    @DeleteMapping("/{id}")
    public Result<Boolean> remove(@PathVariable Long id) {
        taskService.remove(UserContext.requireUserId(), id);
        return Result.success(true);
    }

    @PostMapping("/{id}/status")
    public Result<TaskVO> changeStatus(@PathVariable Long id, @RequestParam Integer status) {
        return Result.success(taskService.changeStatus(UserContext.requireUserId(), id, status));
    }
}
