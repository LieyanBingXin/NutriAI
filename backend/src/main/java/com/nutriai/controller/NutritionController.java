package com.nutriai.controller;

import com.nutriai.dto.ApiResponse;
import com.nutriai.dto.RecommendationResponse;
import com.nutriai.dto.UserProfileRequest;
import com.nutriai.service.AuthService;
import com.nutriai.service.NutritionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class NutritionController {

    private final NutritionService nutritionService;
    private final AuthService authService;

    @PostMapping("/recommend")
    public ApiResponse<RecommendationResponse> recommend(
            @Valid @RequestBody UserProfileRequest profile) {
        Long userId = authService.getCurrentUserId();
        RecommendationResponse response = nutritionService.getRecommendations(profile, userId);
        return ApiResponse.success(response);
    }

    @GetMapping("/ai/providers")
    public ApiResponse<List<String>> listProviders() {
        return ApiResponse.success(nutritionService.listAiProviders());
    }

    @GetMapping("/health")
    public ApiResponse<String> health() {
        return ApiResponse.success("NutriAI is running");
    }
}