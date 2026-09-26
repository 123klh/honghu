package com.honghu.controller;

import com.honghu.annotation.RequireManager;
import com.honghu.common.Result;
import com.honghu.common.UserContext;
import com.honghu.service.StatisticsService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 统计接口。/me 为当前用户学习数据；/admin 为管理端概览，需管理员身份。
 */
@RestController
@RequestMapping("/api/stats")
@RequiredArgsConstructor
public class StatisticsController {

    private final StatisticsService statisticsService;

    @GetMapping("/me")
    public Result<StatisticsService.UserStats> me(@RequestParam(defaultValue = "30") int days) {
        return Result.success(statisticsService.userStats(UserContext.requireUserId(), days));
    }

    @RequireManager
    @GetMapping("/admin/overview")
    public Result<StatisticsService.AdminStats> adminOverview(@RequestParam(defaultValue = "30") int days) {
        return Result.success(statisticsService.adminStats(days));
    }
}
