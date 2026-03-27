package com.campus.lostfound.controller;

import com.campus.lostfound.common.ApiResult;
import com.campus.lostfound.dto.AdminUserCreateRequest;
import com.campus.lostfound.dto.AdminUserUpdateRequest;
import com.campus.lostfound.entity.User;
import com.campus.lostfound.service.AdminGuard;
import com.campus.lostfound.service.AdminUserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/admin/users")
@RequiredArgsConstructor
public class AdminUserController {

    private final AdminGuard adminGuard;
    private final AdminUserService adminUserService;

    @GetMapping
    public ApiResult<Page<User>> list(@RequestParam(required = false) String keyword,
                                      @RequestParam(required = false) Integer userType,
                                      @RequestParam(defaultValue = "0") int page,
                                      @RequestParam(defaultValue = "20") int size) {
        adminGuard.requireAdmin();
        return ApiResult.ok(adminUserService.list(keyword, userType, page, size));
    }

    @PostMapping
    public ApiResult<User> create(@Valid @RequestBody AdminUserCreateRequest body) {
        adminGuard.requireAdmin();
        return ApiResult.ok(adminUserService.create(body));
    }

    @PutMapping("/{id}")
    public ApiResult<User> update(@PathVariable long id, @Valid @RequestBody AdminUserUpdateRequest body) {
        adminGuard.requireAdmin();
        return ApiResult.ok(adminUserService.update(id, body));
    }

    @DeleteMapping("/{id}")
    public ApiResult<Void> delete(@PathVariable long id) {
        adminGuard.requireAdmin();
        adminUserService.delete(id);
        return ApiResult.ok();
    }

    @PostMapping("/{id}/ban")
    public ApiResult<Void> ban(@PathVariable long id) {
        adminGuard.requireAdmin();
        adminUserService.ban(id);
        return ApiResult.ok();
    }

    @PostMapping("/{id}/unban")
    public ApiResult<Void> unban(@PathVariable long id) {
        adminGuard.requireAdmin();
        adminUserService.unban(id);
        return ApiResult.ok();
    }
}
