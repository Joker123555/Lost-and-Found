package com.campus.lostfound.service;

import com.campus.lostfound.dto.*;
import com.campus.lostfound.entity.User;
import com.campus.lostfound.entity.VerificationCode;
import com.campus.lostfound.exception.BusinessException;
import com.campus.lostfound.repository.UserRepository;
import com.campus.lostfound.repository.VerificationCodeRepository;
import com.campus.lostfound.security.JwtService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final WechatMpService wechatMpService;
    private final UserRepository userRepository;
    private final VerificationCodeRepository verificationCodeRepository;
    private final JwtService jwtService;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    @Transactional
    public void sendCode(SendCodeRequest req) {
        if (!"reset".equals(req.getType())) {
            throw new BusinessException("不支持的操作");
        }
        String acc = req.getAccount().trim();
        userRepository.findByAccountAndIsDeleted(acc, 0)
                .orElseThrow(() -> new BusinessException("账号不存在"));
        String code = String.format("%04d", new SecureRandom().nextInt(10000));
        VerificationCode vc = VerificationCode.builder()
                .target(acc)
                .code(code)
                .type(req.getType())
                .expireAt(LocalDateTime.now().plusMinutes(2))
                .build();
        verificationCodeRepository.save(vc);
        System.out.println("[验证码] account=" + acc + " -> " + code + " (开发环境打印)");
    }

    @Transactional
    public TokenResponse register(RegisterRequest req) {
      Integer wantType = req.getUserType();
      if (wantType != null && wantType == 1) {
          throw new BusinessException("微信用户请返回登录页，使用「微信登录」完成注册");
      }
      String acc = req.getAccount().trim();
      if (userRepository.findByAccountAndIsDeleted(acc, 0).isPresent()) {
          throw new BusinessException("账号已存在");
      }
        String nick = (req.getNickname() != null && !req.getNickname().isBlank())
                ? req.getNickname().trim()
                : ("用户" + acc.substring(0, Math.min(4, acc.length())));
        User u = User.builder()
                .account(acc)
                .password(passwordEncoder.encode(req.getPassword()))
                .nickname(nick)
                .userType(0)
                .role(0)
                .status(0)
                .failedLogin(0)
                .isDeleted(0)
                .build();
        u = userRepository.save(u);
        String token = jwtService.createUserToken(u.getId());
        return new TokenResponse(token, u.getId(), u.getNickname());
    }

    @Transactional
    public TokenResponse login(LoginRequest req) {
        User u = userRepository.findByAccountAndIsDeleted(req.getAccount().trim(), 0)
                .orElseThrow(() -> new BusinessException("账号或密码错误"));
        if (u.getStatus() != null && u.getStatus() == 1) {
            throw new BusinessException("账号已封禁");
        }
        if (u.getLockedUntil() != null && u.getLockedUntil().isAfter(LocalDateTime.now())) {
            throw new BusinessException("账号已锁定，请稍后再试");
        }
        if (u.getPassword() == null || !passwordEncoder.matches(req.getPassword(), u.getPassword())) {
            int fails = (u.getFailedLogin() == null ? 0 : u.getFailedLogin()) + 1;
            u.setFailedLogin(fails);
            if (fails >= 5) {
                u.setLockedUntil(LocalDateTime.now().plusMinutes(30));
                u.setFailedLogin(0);
            }
            userRepository.save(u);
            throw new BusinessException("账号或密码错误");
        }
        u.setFailedLogin(0);
        u.setLockedUntil(null);
        userRepository.save(u);
        String token = jwtService.createUserToken(u.getId());
        return new TokenResponse(token, u.getId(), u.getNickname());
    }

    @Transactional
    public TokenResponse wxLogin(WxLoginRequest req) {
        Optional<WechatMpService.WxSession> wxSession = wechatMpService.exchangeCode2Session(req.getCode());
        String openid = wxSession.map(WechatMpService.WxSession::openid)
                .filter(s -> !s.isBlank())
                .orElseThrow(() -> new BusinessException("微信登录失败：未获取到微信唯一标识(openid)，请检查后端微信配置"));

        User u = userRepository.findByOpenidAndIsDeleted(openid, 0).orElseGet(() -> {
            User nu = User.builder()
                    .openid(openid)
                    .nickname("微信用户")
                    .userType(1)
                    .role(0)
                    .status(0)
                    .failedLogin(0)
                    .isDeleted(0)
                    .build();
            return userRepository.save(nu);
        });
        if (u.getStatus() != null && u.getStatus() == 1) {
            throw new BusinessException("账号已封禁");
        }
        u.setUserType(1);

        if (req.getNickname() != null && !req.getNickname().isBlank()) {
            u.setNickname(req.getNickname().trim());
        }
        if (req.getAvatarUrl() != null && !req.getAvatarUrl().isBlank()) {
            u.setAvatarUrl(req.getAvatarUrl().trim());
        }
        userRepository.save(u);

        String token = jwtService.createUserToken(u.getId());
        return new TokenResponse(token, u.getId(), u.getNickname());
    }

    @Transactional
    public void resetPassword(ResetPasswordRequest req) {
        String acc = req.getAccount().trim();
        verifyCode(acc, req.getCode(), "reset");
        User u = userRepository.findByAccountAndIsDeleted(acc, 0)
                .orElseThrow(() -> new BusinessException("用户不存在"));
        u.setPassword(passwordEncoder.encode(req.getNewPassword()));
        userRepository.save(u);
    }

    private void verifyCode(String target, String code, String type) {
        VerificationCode vc = verificationCodeRepository.findTopByTargetAndTypeOrderByCreatedAtDesc(target, type)
                .orElseThrow(() -> new BusinessException("请先获取验证码"));
        if (vc.getExpireAt().isBefore(LocalDateTime.now())) {
            throw new BusinessException("验证码已过期");
        }
        if (!vc.getCode().equals(code)) {
            throw new BusinessException("验证码错误");
        }
    }

}
