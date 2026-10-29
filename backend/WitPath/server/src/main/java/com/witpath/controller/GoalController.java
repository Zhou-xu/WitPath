package com.witpath.controller;

import com.witpath.context.UserContext;
import com.witpath.dto.GoalSaveDTO;
import com.witpath.dto.PageQueryDTO;
import com.witpath.result.PageResult;
import com.witpath.result.Result;
import com.witpath.service.GoalService;
import com.witpath.vo.GoalVO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 学习目标控制器
 */
@RestController
@RequestMapping("/api/goals")
@RequiredArgsConstructor
public class GoalController {

    private final GoalService goalService;

    @GetMapping("/page")
    public Result<PageResult<GoalVO>> page(PageQueryDTO query,
                                           @RequestParam(value = "status", required = false) Integer status) {
        return Result.success(goalService.page(UserContext.requireUserId(), query, status));
    }

    @GetMapping("/{id}")
    public Result<GoalVO> detail(@PathVariable Long id) {
        return Result.success(goalService.detail(UserContext.requireUserId(), id));
    }

    @PostMapping
    public Result<GoalVO> save(@RequestBody GoalSaveDTO dto) {
        return Result.success(goalService.save(UserContext.requireUserId(), dto));
    }

    @DeleteMapping("/{id}")
    public Result<Boolean> remove(@PathVariable Long id) {
        goalService.remove(UserContext.requireUserId(), id);
        return Result.success(true);
    }

    @PostMapping("/{id}/status")
    public Result<GoalVO> changeStatus(@PathVariable Long id,
                                       @RequestParam Integer status) {
        return Result.success(goalService.changeStatus(UserContext.requireUserId(), id, status));
    }
}
