package com.campus.lostfound.service;

import com.campus.lostfound.common.ItemStatus;
import com.campus.lostfound.dto.AdminUserCreateRequest;
import com.campus.lostfound.dto.AdminUserUpdateRequest;
import com.campus.lostfound.entity.Item;
import com.campus.lostfound.entity.User;
import com.campus.lostfound.exception.BusinessException;
import com.campus.lostfound.repository.ItemRepository;
import com.campus.lostfound.repository.UserRepository;
import com.campus.lostfound.security.UserContext;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;

@Service
@RequiredArgsConstructor
public class AdminUserService {

    private final UserRepository userRepository;
    private final ItemRepository itemRepository;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    public Page<User> list(String kw, int page, int size) {
        return userRepository.search(kw == null ? "" : kw, PageRequest.of(page, size));
    }

    @Transactional
    public User create(AdminUserCreateRequest req) {
        if (userRepository.countActiveByPhone(req.getPhone()) > 0) {
            throw new BusinessException("手机号已存在");
        }
        int role = (req.getRole() != null && req.getRole() == 1) ? 1 : 0;
        int status = (req.getStatus() != null && req.getStatus() == 1) ? 1 : 0;
        User u = User.builder()
                .phone(req.getPhone())
                .password(passwordEncoder.encode(req.getPassword()))
                .nickname(req.getNickname().trim())
                .userType(0)
                .role(role)
                .status(status)
                .failedLogin(0)
                .isDeleted(0)
                .build();
        return userRepository.save(u);
    }

    @Transactional
    public User update(long id, AdminUserUpdateRequest req) {
        User u = userRepository.findById(id).orElseThrow(() -> new BusinessException("用户不存在"));
        if (u.getIsDeleted() != null && u.getIsDeleted() == 1) {
            throw new BusinessException("用户已删除");
        }
        if (!req.getPhone().equals(u.getPhone()) && userRepository.countActiveByPhoneExceptId(req.getPhone(), id) > 0) {
            throw new BusinessException("手机号已被占用");
        }
        u.setPhone(req.getPhone());
        u.setNickname(req.getNickname().trim());
        if (req.getRole() != null) {
            u.setRole(req.getRole() == 1 ? 1 : 0);
        }
        if (req.getStatus() != null) {
            u.setStatus(req.getStatus() == 1 ? 1 : 0);
        }
        if (StringUtils.hasText(req.getNewPassword())) {
            if (req.getNewPassword().length() < 8 || req.getNewPassword().length() > 20) {
                throw new BusinessException("新密码长度须为8-20位");
            }
            u.setPassword(passwordEncoder.encode(req.getNewPassword()));
        }
        return userRepository.save(u);
    }

    @Transactional
    public void delete(long userId) {
        Long me = UserContext.getUserId();
        if (me != null && me.equals(userId)) {
            throw new BusinessException("不能删除当前登录账号");
        }
        User u = userRepository.findById(userId).orElseThrow(() -> new BusinessException("用户不存在"));
        if (u.getIsDeleted() != null && u.getIsDeleted() == 1) {
            return;
        }
        u.setIsDeleted(1);
        // 释放 uk_users_phone / uk_users_openid：逻辑删除后若不置空，无法用同一手机号再注册
        u.setPhone(null);
        u.setOpenid(null);
        userRepository.save(u);
    }

    @Transactional
    public void ban(long userId) {
        User u = userRepository.findById(userId).orElseThrow(() -> new BusinessException("用户不存在"));
        if (u.getIsDeleted() != null && u.getIsDeleted() == 1) {
            throw new BusinessException("用户已删除");
        }
        u.setStatus(1);
        userRepository.save(u);
        List<Item> items = itemRepository.findByUserId(userId, PageRequest.of(0, 1000)).getContent();
        for (Item it : items) {
            if (it.getStatus() != null && it.getStatus() == ItemStatus.PUBLISHED) {
                it.setStatus(ItemStatus.OFFLINE);
                itemRepository.save(it);
            }
        }
    }

    @Transactional
    public void unban(long userId) {
        User u = userRepository.findById(userId).orElseThrow(() -> new BusinessException("用户不存在"));
        if (u.getIsDeleted() != null && u.getIsDeleted() == 1) {
            throw new BusinessException("用户已删除");
        }
        u.setStatus(0);
        userRepository.save(u);
    }
}
