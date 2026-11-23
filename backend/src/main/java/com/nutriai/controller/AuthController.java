package com.nutriai.controller;

import com.nutriai.dto.*;
import com.nutriai.exception.AiApiException;
import com.nutriai.service.AuthService;
import com.nutriai.service.PasswordResetService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final PasswordResetService passwordResetService;
    private final SecurityContextRepository securityContextRepository;

    @PostMapping("/register")
    public ApiResponse<AuthResponse> register(@Valid @RequestBody AuthRequest request,
                                              HttpServletRequest httpRequest,
                                              HttpServletResponse httpResponse) {
        AuthResponse response = authService.register(request);
        // 注册成功后直接建立会话，避免前端显示已登录但后续接口全部 401
        try {
            authService.login(request);
            persistSecurityContext(httpRequest, httpResponse);
        } catch (Exception e) {
            // 自动登录失败不应影响注册本身
        }
        return ApiResponse.success(response);
    }

    @PostMapping("/login")
    public ApiResponse<AuthResponse> login(@Valid @RequestBody AuthRequest request,
                                           HttpServletRequest httpRequest,
                                           HttpServletResponse httpResponse) {
        AuthResponse response = authService.login(request);
        persistSecurityContext(httpRequest, httpResponse);
        return ApiResponse.success(response);
    }

    private void persistSecurityContext(HttpServletRequest request, HttpServletResponse response) {
        // 登录成功后轮换 SessionID，防止会话固定攻击
        if (request.getSession(false) != null) {
            try {
                request.changeSessionId();
            } catch (IllegalStateException ignored) {
                // 会话刚被创建/无效时忽略
            }
        }
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(SecurityContextHolder.getContext().getAuthentication());
        SecurityContextHolder.setContext(context);
        securityContextRepository.saveContext(context, request, response);
    }

    @PostMapping("/logout")
    public ApiResponse<String> logout(HttpServletRequest request, HttpServletResponse response) {
        new SecurityContextLogoutHandler().logout(request, response,
                SecurityContextHolder.getContext().getAuthentication());
        return ApiResponse.success("已登出");
    }

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<UserSummaryDto>> me() {
        UserSummaryDto user = authService.getCurrentUser();
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.error(401, "未登录"));
        }
        return ResponseEntity.ok(ApiResponse.success(user));
    }

    @GetMapping("/verify")
    public ApiResponse<String> verifyEmail(@RequestParam String token) {
        return ApiResponse.success(authService.verifyEmail(token));
    }

    @PostMapping("/resend-verify")
    public ApiResponse<String> resendVerify(@RequestBody Map<String, String> body) {
        String username = body.get("username");
        if (username == null || username.isBlank()) {
            throw new AiApiException(400, "用户名不能为空");
        }
        authService.resendVerificationEmail(username);
        return ApiResponse.success("验证邮件已重新发送");
    }

    @PostMapping("/forgot-password")
    public ApiResponse<String> forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
        passwordResetService.requestReset(request.getEmail());
        return ApiResponse.success("如果该邮箱已注册，重置链接将发送到您的邮箱");
    }

    @PostMapping("/reset-password")
    public ApiResponse<String> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        passwordResetService.reset(request.getToken(), request.getNewPassword());
        return ApiResponse.success("密码重置成功");
    }

    @PutMapping("/me/display-name")
    public ApiResponse<UserSummaryDto> updateDisplayName(@RequestBody Map<String, String> body) {
        String displayName = body.get("displayName");
        if (displayName == null || displayName.isBlank()) {
            throw new AiApiException(400, "昵称不能为空");
        }
        if (displayName.trim().length() > 50) {
            throw new AiApiException(400, "昵称不能超过 50 个字符");
        }
        return ApiResponse.success(authService.updateDisplayName(displayName.trim()));
    }
}
