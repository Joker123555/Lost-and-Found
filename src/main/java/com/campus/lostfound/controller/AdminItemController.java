package com.campus.lostfound.controller;

import com.campus.lostfound.common.ApiResult;
import com.campus.lostfound.entity.Item;
import com.campus.lostfound.service.AdminGuard;
import com.campus.lostfound.service.AdminItemService;
import com.campus.lostfound.service.ItemService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.Map;

@RestController
@RequestMapping("/admin/items")
@RequiredArgsConstructor
public class AdminItemController {

    private final AdminGuard adminGuard;
    private final AdminItemService adminItemService;
    private final ItemService itemService;

    @GetMapping("/pending")
    public ApiResult<Page<Item>> pending(@RequestParam(required = false) Integer type,
                                         @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
                                         @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to,
                                         @RequestParam(defaultValue = "0") int page,
                                         @RequestParam(defaultValue = "20") int size) {
        adminGuard.requireAdmin();
        return ApiResult.ok(adminItemService.pending(type, from, to, page, size));
    }

    @GetMapping("/browse")
    public ApiResult<Page<java.util.Map<String, Object>>> browse(@RequestParam int type,
                                                                 @RequestParam(required = false) String keyword,
                                                                 @RequestParam(required = false) String contactName,
                                                                 @RequestParam(required = false) Long categoryId,
                                                                 @RequestParam(required = false) Integer status,
                                                                 @RequestParam(defaultValue = "0") int page,
                                                                 @RequestParam(defaultValue = "10") int size) {
        adminGuard.requireAdmin();
        return ApiResult.ok(adminItemService.browse(type, keyword, contactName, categoryId, status, page, size));
    }

    @DeleteMapping("/{id}")
    public ApiResult<Void> delete(@PathVariable long id) {
        adminGuard.requireAdmin();
        adminItemService.softDelete(id);
        return ApiResult.ok();
    }

    @GetMapping("/detail/{id}")
    public ApiResult<Map<String, Object>> detail(@PathVariable long id) {
        adminGuard.requireAdmin();
        return ApiResult.ok(itemService.detailWithImages(id));
    }

    @PostMapping("/{id}/approve")
    public ApiResult<Void> approve(@PathVariable long id) {
        adminGuard.requireAdmin();
        adminItemService.approve(id);
        return ApiResult.ok();
    }

    @PostMapping("/{id}/reject")
    public ApiResult<Void> reject(@PathVariable long id, @RequestBody RejectBody body) {
        adminGuard.requireAdmin();
        adminItemService.reject(id, body.getReason());
        return ApiResult.ok();
    }

    @Data
    public static class RejectBody {
        private String reason;
    }
}
