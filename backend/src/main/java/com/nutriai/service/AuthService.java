package com.nutriai.service;

import com.nutriai.dto.AuthRequest;
import com.nutriai.dto.AuthResponse;
import com.nutriai.dto.UserSummaryDto;
import com.nutriai.entity.User;
import com.nutriai.exception.AiApiException;
import com.nutriai.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final EmailService emailService;

    @Transactional
    public AuthResponse register(AuthRequest request) {
        String username = request.getUsername() == null ? "" : request.getUsername().trim();
        if (username.length() < 3) {
            throw new AiApiException(400, "用户名至少 3 个字符");
        }
        String email = request.getEmail() == null ? "" : request.getEmail().trim().toLowerCase(java.util.Locale.ROOT);

        if (userRepository.existsByUsername(username)) {
            throw new AiApiException(400, "用户名已存在");
        }
        if (!email.isBlank() && userRepository.existsByEmail(email)) {
            throw new AiApiException(400, "邮箱已被注册");
        }

        User user = new User(
                username,
                email.isBlank() ? username + "@nutriai.local" : email,
                passwordEncoder.encode(request.getPassword())
        );
        if (request.getDisplayName() != null && !request.getDisplayName().isBlank()) {
            user.setDisplayName(request.getDisplayName());
        }

        if (!email.isBlank()) {
            String token = generateToken();
            user.setVerificationToken(token);
            user.setVerificationTokenExpiry(LocalDateTime.now().plusHours(24));
            user = userRepository.save(user);

            try {
                if (emailService.isConfigured()) {
                    emailService.sendVerificationEmail(user.getEmail(), user.getUsername(), token);
                    log.info("验证邮件已发送: userId={}", user.getId());
                } else {
                    log.info("邮件服务未配置，跳过发送验证邮件（开发环境可在 DEBUG 日志查看 token）");
                    log.debug("verification token for userId={}: {}", user.getId(), token);
                }
            } catch (Exception e) {
                log.warn("验证邮件发送失败: {}", e.getMessage());
            }
        } else {
            user.setEmailVerified(true);
            user = userRepository.save(user);
        }

        log.info("新用户注册: {}", user.getUsername());
        return new AuthResponse(user.getId(), user.getUsername(), user.getEmail(),
                user.getDisplayName(), "注册成功" + (user.isEmailVerified() ? "" : "，请查收邮件完成验证"));
    }

    public AuthResponse login(AuthRequest request) {
        String loginName = request.getUsername() == null ? "" : request.getUsername().trim();
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(loginName, request.getPassword())
        );
        SecurityContextHolder.getContext().setAuthentication(authentication);

        User user = userRepository.findByUsername(loginName)
                .or(() -> userRepository.findByEmail(loginName.toLowerCase(java.util.Locale.ROOT)))
                .orElseThrow(() -> new AiApiException(401, "用户名或密码错误"));
        log.info("用户登录: {}", user.getUsername());
        return new AuthResponse(user.getId(), user.getUsername(), user.getEmail(), user.getDisplayName(), "登录成功");
    }

    public void logout() {
        SecurityContextHolder.clearContext();
    }

    public UserSummaryDto getCurrentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getPrincipal())) {
            return null;
        }
        User user = userRepository.findByUsername(auth.getName()).orElse(null);
        if (user == null) return null;
        return new UserSummaryDto(user.getId(), user.getUsername(), user.getEmail(),
                user.getDisplayName(), user.getCreatedAt(), user.getUpdatedAt());
    }

    public Long getCurrentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getPrincipal())) {
            return null;
        }
        User user = userRepository.findByUsername(auth.getName()).orElse(null);
        return user != null ? user.getId() : null;
    }

    @Transactional
    public String verifyEmail(String token) {
        User user = userRepository.findByVerificationToken(token)
                .orElseThrow(() -> new AiApiException(400, "无效的验证链接"));

        if (user.getVerificationTokenExpiry() == null
                || LocalDateTime.now().isAfter(user.getVerificationTokenExpiry())) {
            throw new AiApiException(400, "验证链接已过期，请重新申请");
        }

        user.setEmailVerified(true);
        user.setVerificationToken(null);
        user.setVerificationTokenExpiry(null);
        userRepository.save(user);

        log.info("邮箱验证成功: userId={}", user.getId());
        return "邮箱验证成功！";
    }

    @Transactional
    public void resendVerificationEmail(String username) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new AiApiException(404, "用户不存在"));

        if (user.isEmailVerified()) {
            throw new AiApiException(400, "邮箱已验证，无需再次发送");
        }

        String token = generateToken();
        user.setVerificationToken(token);
        user.setVerificationTokenExpiry(LocalDateTime.now().plusHours(24));
        userRepository.save(user);

        if (emailService.isConfigured()) {
            emailService.sendVerificationEmail(user.getEmail(), user.getUsername(), token);
            log.info("验证邮件重发: userId={}", user.getId());
        } else {
            log.info("邮件服务未配置，验证邮件未发送");
            log.debug("verification token for userId={}: {}", user.getId(), token);
        }
    }

    private String generateToken() {
        SecureRandom random = new SecureRandom();
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    @Transactional
    public UserSummaryDto updateDisplayName(String displayName) {
        User user = getCurrentUserEntity();
        if (user == null) {
            throw new AiApiException(401, "未登录");
        }
        user.setDisplayName(displayName);
        user = userRepository.save(user);
        return new UserSummaryDto(user.getId(), user.getUsername(), user.getEmail(),
                user.getDisplayName(), user.getCreatedAt(), user.getUpdatedAt());
    }

    private User getCurrentUserEntity() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getPrincipal())) {
            return null;
        }
        return userRepository.findByUsername(auth.getName()).orElse(null);
    }
}