package com.nutriai.controller;

import com.nutriai.dto.ApiResponse;
import com.nutriai.dto.AuthRequest;
import com.nutriai.dto.AuthResponse;
import com.nutriai.dto.UserSummaryDto;
import com.nutriai.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final SecurityContextRepository securityContextRepository;

    @PostMapping("/register")
    public ApiResponse<AuthResponse> register(@Valid @RequestBody AuthRequest request) {
        AuthResponse response = authService.register(request);
        return ApiResponse.success(response);
    }

    @PostMapping("/login")
    public ApiResponse<AuthResponse> login(@Valid @RequestBody AuthRequest request,
                                            HttpServletRequest httpRequest,
                                            HttpServletResponse httpResponse) {
        AuthResponse response = authService.login(request);

        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(SecurityContextHolder.getContext().getAuthentication());
        SecurityContextHolder.setContext(context);
        securityContextRepository.saveContext(context, httpRequest, httpResponse);

        return ApiResponse.success(response);
    }

    @PostMapping("/logout")
    public ApiResponse<String> logout(HttpServletRequest request, HttpServletResponse response) {
        new SecurityContextLogoutHandler().logout(request, response,
                SecurityContextHolder.getContext().getAuthentication());
        return ApiResponse.success("已登出");
    }

    @GetMapping("/me")
    public ApiResponse<UserSummaryDto> me() {
        UserSummaryDto user = authService.getCurrentUser();
        if (user == null) {
            return ApiResponse.error(401, "未登录");
        }
        return ApiResponse.success(user);
    }
}