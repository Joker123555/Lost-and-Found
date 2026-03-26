package com.campus.lostfound.controller;

import com.campus.lostfound.common.ApiResult;
import com.campus.lostfound.service.MatchComputeService;
import com.campus.lostfound.service.MatchQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/matches")
@RequiredArgsConstructor
public class ApiMatchController {

    private final MatchQueryService matchQueryService;
    private final MatchComputeService matchComputeService;

    @GetMapping
    public ApiResult<List<Map<String, Object>>> list() {
        return ApiResult.ok(matchQueryService.myMatches());
    }

    @GetMapping("/meta")
    public ApiResult<Map<String, Object>> meta() {
        return ApiResult.ok(matchQueryService.meta());
    }

    @GetMapping("/{id}")
    public ApiResult<Map<String, Object>> detail(@PathVariable long id) {
        return ApiResult.ok(matchQueryService.matchDetail(id));
    }

    /** 开发/测试场景：用 SQL 导入样例后可主动触发一次重算，无需等待定时任务 */
    @PostMapping("/recompute")
    public ApiResult<Boolean> recomputeNow() {
        matchComputeService.recomputeAll();
        return ApiResult.ok(true);
    }
}
