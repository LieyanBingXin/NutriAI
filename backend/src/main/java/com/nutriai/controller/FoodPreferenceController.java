package com.nutriai.controller;

import com.nutriai.dto.ApiResponse;
import com.nutriai.entity.FoodPreference;
import com.nutriai.exception.AiApiException;
import com.nutriai.service.AuthService;
import com.nutriai.service.FoodPreferenceService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/food-preference")
@RequiredArgsConstructor
public class FoodPreferenceController {

    private final FoodPreferenceService service;
    private final AuthService authService;

    @GetMapping
    public ApiResponse<List<FoodPreference>> list() {
        Long userId = requireLogin();
        return ApiResponse.success(service.getUserPreferences(userId));
    }

    @PostMapping
    public ApiResponse<FoodPreference> set(@RequestBody Map<String, String> body) {
        Long userId = requireLogin();
        String foodName = body.get("foodName");
        String preference = body.get("preference");
        if (foodName == null || foodName.isBlank()) {
            throw new AiApiException(400, "食物名称不能为空");
        }
        if (foodName.trim().length() > 200) {
            throw new AiApiException(400, "食物名称过长");
        }
        if (preference == null || preference.isBlank()) {
            throw new AiApiException(400, "偏好值不能为空");
        }
        FoodPreference pref = service.setPreference(userId,
                foodName.trim(),
                body.get("category"),
                preference.trim().toUpperCase(java.util.Locale.ROOT));
        return ApiResponse.success(pref);
    }

    @DeleteMapping
    public ApiResponse<String> delete(@RequestParam String foodName) {
        Long userId = requireLogin();
        service.deletePreference(userId, foodName);
        return ApiResponse.success("已删除");
    }

    private Long requireLogin() {
        Long userId = authService.getCurrentUserId();
        if (userId == null) throw new AiApiException(401, "请先登录");
        return userId;
    }
}