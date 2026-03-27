package com.campus.lostfound.controller;

import com.campus.lostfound.common.ApiResult;
import com.campus.lostfound.entity.OperationLog;
import com.campus.lostfound.service.AdminGuard;
import com.campus.lostfound.service.OperationLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin/operation-logs")
@RequiredArgsConstructor
public class AdminOperationLogController {

    private final AdminGuard adminGuard;
    private final OperationLogService operationLogService;

    @GetMapping
    public ApiResult<Page<OperationLog>> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String keyword
    ) {
        adminGuard.requireAdmin();
        return ApiResult.ok(operationLogService.list(type, keyword, page, size));
    }
}
