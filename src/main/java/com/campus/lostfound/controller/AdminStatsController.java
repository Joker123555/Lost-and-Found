package com.campus.lostfound.controller;

import com.campus.lostfound.common.ApiResult;
import com.campus.lostfound.service.AdminGuard;
import com.campus.lostfound.service.StatsService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/admin/stats")
@RequiredArgsConstructor
public class AdminStatsController {

    private final AdminGuard adminGuard;
    private final StatsService statsService;

    @GetMapping("/overview")
    public ApiResult<Map<String, Object>> overview() {
        adminGuard.requireAdmin();
        return ApiResult.ok(statsService.overview());
    }

    @GetMapping("/trend")
    public ApiResult<Map<String, Object>> trend(@RequestParam(defaultValue = "7") int days) {
        adminGuard.requireAdmin();
        return ApiResult.ok(statsService.trend(days));
    }

    @GetMapping("/category-ratio")
    public ApiResult<List<Map<String, Object>>> categoryRatio() {
        adminGuard.requireAdmin();
        return ApiResult.ok(statsService.categoryRatio());
    }
}
