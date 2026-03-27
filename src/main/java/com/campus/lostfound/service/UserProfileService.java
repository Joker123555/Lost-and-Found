package com.campus.lostfound.service;

import com.campus.lostfound.entity.User;
import com.campus.lostfound.exception.BusinessException;
import com.campus.lostfound.repository.UserRepository;
import com.campus.lostfound.security.UserContext;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

@Service
@RequiredArgsConstructor
public class UserProfileService {

    private static final String PASSWORD_REGEX = "^(?=.*[A-Za-z])(?=.*\\d)[A-Za-z\\d]{8,24}$";

    private final UserRepository userRepository;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    public Map<String, Object> me() {
        User u = userRepository.findById(UserContext.getUserId()).orElseThrow();
        int ut = u.getUserType() == null ? 0 : u.getUserType();
        boolean isWx = ut == 1 || (u.getOpenid() != null && !u.getOpenid().isBlank());
        return Map.of(
                "id", u.getId(),
                "nickname", u.getNickname(),
                "avatarUrl", u.getAvatarUrl() == null ? "" : u.getAvatarUrl(),
                "account", u.getAccount() == null ? "" : u.getAccount(),
                "userType", ut,
                "isWxUser", isWx,
                "phone", u.getPhone() == null ? "" : u.getPhone(),
                "role", u.getRole() == null ? 0 : u.getRole(),
                "hasPassword", u.getPassword() != null && !u.getPassword().isBlank()
        );
    }

    @Transactional
    public void updateProfile(String nickname, String avatarUrl) {
        User u = userRepository.findById(UserContext.getUserId()).orElseThrow();
        if (nickname != null && !nickname.isBlank()) {
            String n = nickname.trim();
            if (n.length() > 64) n = n.substring(0, 64);
            u.setNickname(n);
        }
        if (avatarUrl != null && !avatarUrl.isBlank()) {
            String a = avatarUrl.trim();
            if (a.length() > 512) {
                throw new BusinessException("头像地址过长，请重新选择头像上传");
            }
            u.setAvatarUrl(a);
        }
        userRepository.save(u);
    }

    @Transactional
    public void changePassword(String oldPassword, String newPassword) {
        if (newPassword == null || !newPassword.matches(PASSWORD_REGEX)) {
            throw new BusinessException("新密码需为8-24位字母和数字组合");
        }
        User u = userRepository.findById(UserContext.getUserId()).orElseThrow();
        boolean hasPassword = u.getPassword() != null && !u.getPassword().isBlank();
        if (hasPassword && (oldPassword == null || !passwordEncoder.matches(oldPassword, u.getPassword()))) {
            throw new BusinessException("原密码错误");
        }
        if (hasPassword && passwordEncoder.matches(newPassword, u.getPassword())) {
            throw new BusinessException("新密码不能和老密码一致");
        }
        u.setPassword(passwordEncoder.encode(newPassword));
        userRepository.save(u);
    }
}
