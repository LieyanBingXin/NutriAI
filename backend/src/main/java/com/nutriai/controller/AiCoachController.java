package com.nutriai.controller;

import com.nutriai.dto.ApiResponse;
import com.nutriai.dto.CoachFeedbackRequest;
import com.nutriai.dto.CoachFeedbackResponse;
import com.nutriai.dto.FridgeRequest;
import com.nutriai.dto.NutritionGapDto;
import com.nutriai.dto.RecommendationResponse;
import com.nutriai.service.AiCoachService;
import com.nutriai.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 「AI 教练」—— 营养缺口雷达 / 实时反馈 / 冰箱有料
 */
@RestController
@RequestMapping("/api/coach")
@RequiredArgsConstructor
public class AiCoachController {

    private final AiCoachService aiCoachService;
    private final AuthService authService;

    @GetMapping("/gap")
    public ApiResponse<NutritionGapDto> gap() {
        // 只允许查询当前登录用户自己的缺口，忽略任何外部传入的 userId
        Long userId = authService.getCurrentUserId();
        // 无画像时构造一个通用画像用于可能的 AI 解读
        com.nutriai.dto.UserProfileRequest profile = new com.nutriai.dto.UserProfileRequest();
        profile.setAge(30);
        profile.setHeightCm(170.0);
        profile.setWeightKg(65.0);
        return ApiResponse.success(aiCoachService.getNutritionGap(userId, profile));
    }

    @PostMapping("/feedback")
    public ApiResponse<CoachFeedbackResponse> feedback(@Valid @RequestBody CoachFeedbackRequest req) {
        Long userId = authService.getCurrentUserId();
        return ApiResponse.success(aiCoachService.getCoachFeedback(userId, req));
    }

    @PostMapping("/fridge")
    public ApiResponse<RecommendationResponse> fridge(@Valid @RequestBody FridgeRequest req) {
        Long userId = authService.getCurrentUserId();
        return ApiResponse.success(aiCoachService.getFridgeRecommendation(userId, req));
    }
}