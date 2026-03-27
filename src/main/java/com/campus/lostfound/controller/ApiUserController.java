package com.campus.lostfound.controller;

import com.campus.lostfound.common.ApiResult;
import com.campus.lostfound.service.UserProfileService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class ApiUserController {

    private final UserProfileService userProfileService;

    @GetMapping("/me")
    public ApiResult<Map<String, Object>> me() {
        return ApiResult.ok(userProfileService.me());
    }

    @PutMapping("/me")
    public ApiResult<Void> update(@RequestBody ProfileBody body) {
        userProfileService.updateProfile(body.getNickname(), body.getAvatarUrl(), body.getPhone());
        return ApiResult.ok();
    }

    @PostMapping("/me/password")
    public ApiResult<Void> pwd(@RequestBody PwdBody body) {
        userProfileService.changePassword(body.getOldPassword(), body.getNewPassword());
        return ApiResult.ok();
    }

    @Data
    public static class ProfileBody {
        private String nickname;
        private String avatarUrl;
        private String phone;
    }

    @Data
    public static class PwdBody {
        private String oldPassword;
        private String newPassword;
    }
}
