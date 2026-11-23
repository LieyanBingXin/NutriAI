package com.nutriai.service;

import com.nutriai.entity.User;
import com.nutriai.exception.AiApiException;
import com.nutriai.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;

@Slf4j
@Service
@RequiredArgsConstructor
public class PasswordResetService {

    private final UserRepository userRepository;
    private final EmailService emailService;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public void requestReset(String email) {
        String normalized = email == null ? "" : email.trim().toLowerCase(java.util.Locale.ROOT);
        User user = userRepository.findByEmail(normalized).orElse(null);
        if (user == null) {
            // 不暴露邮箱是否注册，统一返回成功提示，避免账号枚举
            log.info("密码重置请求忽略：邮箱未注册");
            return;
        }

        String token = generateToken();
        user.setResetPasswordToken(token);
        user.setResetPasswordTokenExpiry(LocalDateTime.now().plusHours(1));
        userRepository.save(user);

        try {
            emailService.sendPasswordResetEmail(user.getEmail(), user.getUsername(), token);
        } catch (Exception e) {
            log.warn("邮件发送失败，但重置 token 已生成: {}", e.getMessage());
        }

        log.info("密码重置请求: userId={}", user.getId());
        log.debug("password reset token for userId={}: {}", user.getId(), token);
    }

    @Transactional
    public void reset(String token, String newPassword) {
        User user = userRepository.findByResetPasswordToken(token)
                .orElseThrow(() -> new AiApiException(400, "无效的重置链接"));

        if (user.getResetPasswordTokenExpiry() == null
                || LocalDateTime.now().isAfter(user.getResetPasswordTokenExpiry())) {
            throw new AiApiException(400, "重置链接已过期，请重新申请");
        }

        user.setPassword(passwordEncoder.encode(newPassword));
        user.setResetPasswordToken(null);
        user.setResetPasswordTokenExpiry(null);
        userRepository.save(user);

        log.info("密码重置成功: userId={}", user.getId());
    }

    private String generateToken() {
        SecureRandom random = new SecureRandom();
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}