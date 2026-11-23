package com.nutriai.controller;

import com.nutriai.dto.ApiResponse;
import com.nutriai.dto.NutrientGoalDto;
import com.nutriai.dto.RecommendationResponse;
import com.nutriai.dto.UserProfileRequest;
import com.nutriai.service.AuthService;
import com.nutriai.service.NutrientGoalService;
import com.nutriai.service.NutritionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

import java.util.List;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class NutritionController {

    private final NutritionService nutritionService;
    private final NutrientGoalService nutrientGoalService;
    private final AuthService authService;

    @PostMapping("/recommend")
    public ApiResponse<RecommendationResponse> recommend(
            @Valid @RequestBody UserProfileRequest profile,
            @RequestParam(defaultValue = "true") boolean usePreferences) {
        Long userId = authService.getCurrentUserId();
        RecommendationResponse response = nutritionService.getRecommendations(profile, userId, usePreferences);
        return ApiResponse.success(response);
    }

    @PostMapping(value = "/recommend/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> streamRecommend(@Valid @RequestBody UserProfileRequest profile,
                                        @RequestParam(defaultValue = "true") boolean usePreferences) {
        Long userId = authService.getCurrentUserId();
        if (userId != null) {
            return nutritionService.streamRecommendations(profile, userId, usePreferences);
        }
        return nutritionService.streamRecommendations(profile, null, usePreferences);
    }

    @GetMapping("/ai/providers")
    public ApiResponse<List<String>> listProviders() {
        return ApiResponse.success(nutritionService.listAiProviders());
    }

    @PostMapping("/recommend/goals")
    public ApiResponse<NutrientGoalDto> recommendGoals(@Valid @RequestBody UserProfileRequest profile) {
        NutrientGoalDto goals = nutrientGoalService.calculate(
                profile.getGender() != null ? profile.getGender().name() : "MALE",
                profile.getAge() != null ? profile.getAge() : 30,
                profile.getHeightCm() != null ? profile.getHeightCm() : 170,
                profile.getWeightKg() != null ? profile.getWeightKg() : 65,
                profile.getActivityLevel() != null ? profile.getActivityLevel().name() : "MODERATE",
                profile.getHealthGoal() != null ? profile.getHealthGoal().name() : "MAINTAIN"
        );
        return ApiResponse.success(goals);
    }

    @GetMapping("/health")
    public ApiResponse<String> health() {
        return ApiResponse.success("NutriAI is running");
    }
}