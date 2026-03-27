package com.campus.lostfound.config;

import com.campus.lostfound.entity.User;
import com.campus.lostfound.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

@Configuration
@RequiredArgsConstructor
public class AdminInitConfig {

    private final UserRepository userRepository;

    @Bean
    public CommandLineRunner initAdmin() {
        return args -> {
            if (userRepository.findByAccountAndIsDeleted("admin", 0).isPresent()) {
                return;
            }
            // 兼容旧库：仅有手机号的管理员
            userRepository.findByPhoneAndIsDeleted("13800138000", 0).ifPresent(u -> {
                u.setAccount("admin");
                userRepository.save(u);
                System.out.println("[初始化] 已为原管理员账号补充登录名 admin");
            });
            if (userRepository.findByAccountAndIsDeleted("admin", 0).isPresent()) {
                return;
            }
            BCryptPasswordEncoder enc = new BCryptPasswordEncoder();
            User admin = User.builder()
                    .account("admin")
                    .password(enc.encode("Admin12345"))
                    .nickname("系统管理员")
                    .userType(0)
                    .role(1)
                    .status(0)
                    .failedLogin(0)
                    .isDeleted(0)
                    .build();
            userRepository.save(admin);
            System.out.println("[初始化] 已创建默认管理员 账号 admin / 密码 Admin12345");
        };
    }
}
