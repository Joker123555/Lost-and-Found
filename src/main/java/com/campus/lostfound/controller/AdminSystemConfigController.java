package com.campus.lostfound.controller;

import com.campus.lostfound.common.ApiResult;
import com.campus.lostfound.entity.SystemConfig;
import com.campus.lostfound.exception.BusinessException;
import com.campus.lostfound.repository.SystemConfigRepository;
import com.campus.lostfound.service.AdminGuard;
import com.campus.lostfound.service.OperationLogService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Comparator;
import java.util.List;

@RestController
@RequestMapping("/admin/system-config")
@RequiredArgsConstructor
public class AdminSystemConfigController {

    private final AdminGuard adminGuard;
    private final SystemConfigRepository systemConfigRepository;
    private final OperationLogService operationLogService;

    @GetMapping
    public ApiResult<List<SystemConfig>> list() {
        adminGuard.requireAdmin();
        List<SystemConfig> rows = systemConfigRepository.findAll().stream()
                .sorted(Comparator.comparing(SystemConfig::getConfigKey))
                .toList();
        return ApiResult.ok(rows);
    }

    @PutMapping("/{key}")
    public ApiResult<SystemConfig> update(@PathVariable String key, @RequestBody UpdateBody body, HttpServletRequest request) {
        adminGuard.requireAdmin();
        SystemConfig cfg = systemConfigRepository.findById(key).orElseThrow(() -> new BusinessException("配置项不存在"));
        String value = body == null || body.getConfigValue() == null ? "" : body.getConfigValue().trim();
        if (value.isEmpty()) throw new BusinessException("配置值不能为空");
        cfg.setConfigValue(value);
        if (body.getRemark() != null) cfg.setRemark(body.getRemark().trim());
        SystemConfig saved = systemConfigRepository.save(cfg);
        operationLogService.log("SYSTEM_CONFIG_UPDATE", "修改配置：" + key + " => " + value, request);
        return ApiResult.ok(saved);
    }

    @Data
    public static class UpdateBody {
        private String configValue;
        private String remark;
    }
}
