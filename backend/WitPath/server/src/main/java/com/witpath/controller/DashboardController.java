package com.witpath.controller;

import com.witpath.context.UserContext;
import com.witpath.result.Result;
import com.witpath.service.DashboardService;
import com.witpath.vo.DashboardVO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 概览页控制器
 */
@RestController
@RequestMapping("/api/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final DashboardService dashboardService;

    @GetMapping("/overview")
    public Result<DashboardVO> overview(@RequestParam(value = "days", defaultValue = "14") Integer days) {
        return Result.success(dashboardService.overview(UserContext.requireUserId(), days));
    }
}
