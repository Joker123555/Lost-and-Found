package com.campus.lostfound.controller;

import com.campus.lostfound.common.ApiResult;
import com.campus.lostfound.entity.Announcement;
import com.campus.lostfound.service.AdminGuard;
import com.campus.lostfound.service.AnnouncementService;
import com.campus.lostfound.service.OperationLogService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/admin/announcements")
@RequiredArgsConstructor
public class AdminAnnouncementController {

    private final AdminGuard adminGuard;
    private final AnnouncementService announcementService;
    private final OperationLogService operationLogService;

    @GetMapping
    public ApiResult<Page<Announcement>> list(@RequestParam(defaultValue = "0") int page,
                                                @RequestParam(defaultValue = "20") int size) {
        adminGuard.requireAdmin();
        return ApiResult.ok(announcementService.list(page, size));
    }

    @PostMapping
    public ApiResult<Announcement> save(@RequestBody AnnBody body, HttpServletRequest request) {
        adminGuard.requireAdmin();
        Announcement saved = announcementService.save(body.getTitle(), body.getContent(), body.getId());
        operationLogService.log("ANNOUNCEMENT_" + (body.getId() == null ? "CREATE" : "UPDATE"),
                (body.getId() == null ? "新增公告：" : "更新公告：") + saved.getTitle() + " (id=" + saved.getId() + ")",
                request);
        return ApiResult.ok(saved);
    }

    @DeleteMapping("/{id}")
    public ApiResult<Void> delete(@PathVariable long id, HttpServletRequest request) {
        adminGuard.requireAdmin();
        announcementService.delete(id);
        operationLogService.log("ANNOUNCEMENT_DELETE", "删除公告 id=" + id, request);
        return ApiResult.ok();
    }

    @Data
    public static class AnnBody {
        private Long id;
        private String title;
        private String content;
    }
}
