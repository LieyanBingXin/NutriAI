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

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;

    public AuthResponse register(AuthRequest request) {
        if (userRepository.existsByUsername(request.getUsername())) {
            throw new AiApiException(400, "用户名已存在");
        }
        if (request.getEmail() != null && userRepository.existsByEmail(request.getEmail())) {
            throw new AiApiException(400, "邮箱已被注册");
        }

        User user = new User(
                request.getUsername(),
                request.getEmail() != null ? request.getEmail() : request.getUsername() + "@nutriai.local",
                passwordEncoder.encode(request.getPassword())
        );
        if (request.getDisplayName() != null && !request.getDisplayName().isBlank()) {
            user.setDisplayName(request.getDisplayName());
        }
        user = userRepository.save(user);

        log.info("新用户注册: {}", user.getUsername());
        return new AuthResponse(user.getId(), user.getUsername(), user.getEmail(), user.getDisplayName(), "注册成功");
    }

    public AuthResponse login(AuthRequest request) {
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.getUsername(), request.getPassword())
        );
        SecurityContextHolder.getContext().setAuthentication(authentication);

        User user = userRepository.findByUsername(request.getUsername()).orElseThrow();
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
        return new UserSummaryDto(user.getId(), user.getUsername(), user.getEmail(), user.getDisplayName(), user.getCreatedAt(), user.getUpdatedAt());
    }

    public Long getCurrentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getPrincipal())) {
            return null;
        }
        User user = userRepository.findByUsername(auth.getName()).orElse(null);
        return user != null ? user.getId() : null;
    }
}