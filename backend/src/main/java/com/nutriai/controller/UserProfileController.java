package com.nutriai.controller;

import com.nutriai.dto.ApiResponse;
import com.nutriai.dto.UserProfileDto;
import com.nutriai.exception.AiApiException;
import com.nutriai.service.AuthService;
import com.nutriai.service.UserProfileService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/user/profile")
@RequiredArgsConstructor
public class UserProfileController {

    private final UserProfileService userProfileService;
    private final AuthService authService;

    @GetMapping
    public ApiResponse<UserProfileDto> get() {
        Long userId = authService.getCurrentUserId();
        if (userId == null) {
            throw new AiApiException(401, "请先登录");
        }
        UserProfileDto profile = userProfileService.getProfile(userId);
        return ApiResponse.success(profile);
    }

    @PutMapping
    public ApiResponse<UserProfileDto> save(@RequestBody UserProfileDto dto) {
        Long userId = authService.getCurrentUserId();
        if (userId == null) {
            throw new AiApiException(401, "请先登录");
        }
        return ApiResponse.success(userProfileService.saveProfile(userId, dto));
    }

    @DeleteMapping
    public ApiResponse<String> delete() {
        Long userId = authService.getCurrentUserId();
        if (userId == null) {
            throw new AiApiException(401, "请先登录");
        }
        userProfileService.deleteProfile(userId);
        return ApiResponse.success("已清除画像");
    }
}