package com.campus.lostfound.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(length = 64, unique = true)
    private String openid;

    /** 账号密码登录用，与微信 openid 独立；微信用户可为空 */
    @Column(length = 64, unique = true)
    private String account;

    @Column(length = 11, unique = true)
    private String phone;

    @JsonIgnore
    private String password;

    @Column(nullable = false, length = 64)
    private String nickname;

    @Column(name = "avatar_url", length = 512)
    private String avatarUrl;

    @Column(nullable = false)
    private Integer role;

    @Column(nullable = false)
    private Integer status;

    @Column(name = "failed_login", nullable = false)
    private Integer failedLogin;

    @Column(name = "locked_until")
    private LocalDateTime lockedUntil;

    @Column(name = "is_deleted", nullable = false)
    private Integer isDeleted;

    /** 0=普通用户，1=微信用户（注册时选择；微信登录创建的账号为1） */
    @Column(name = "user_type", nullable = false)
    private Integer userType;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    public void prePersist() {
        LocalDateTime now = LocalDateTime.now();
        if (createdAt == null) createdAt = now;
        if (updatedAt == null) updatedAt = now;
        if (nickname == null) nickname = "用户";
        if (role == null) role = 0;
        if (status == null) status = 0;
        if (failedLogin == null) failedLogin = 0;
        if (isDeleted == null) isDeleted = 0;
        if (userType == null) {
            userType = (openid != null && !openid.isBlank()) ? 1 : 0;
        }
    }

    @PreUpdate
    public void preUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
