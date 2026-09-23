package com.nutriai.service;

import com.nutriai.ai.AiService;
import com.nutriai.ai.AiServiceFactory;
import com.nutriai.dto.RecommendationResponse;
import com.nutriai.dto.UserProfileRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class NutritionService {

    private final AiServiceFactory aiServiceFactory;
    private final AuthService authService;
    private final HistoryService historyService;

    public RecommendationResponse getRecommendations(UserProfileRequest profile) {
        return getRecommendations(profile, null);
    }

    public RecommendationResponse getRecommendations(UserProfileRequest profile, Long userId) {
        log.info("开始生成推荐, user={}, age={}", profile.getGender(), profile.getAge());

        AiService ai = aiServiceFactory.getService();
        log.info("使用 AI provider: {}", ai.getProviderName());

        RecommendationResponse response = ai.analyzeAndRecommend(profile);

        log.info("推荐生成完成, 食谱数量={}", response.getRecipes() == null ? 0 : response.getRecipes().size());

        if (userId != null) {
            try {
                historyService.saveRecord(userId, profile, response, response.getAiProvider());
                log.info("推荐已保存到用户历史, userId={}", userId);
            } catch (Exception e) {
                log.warn("保存历史记录失败（不影响推荐结果）: {}", e.getMessage());
            }
        }

        return response;
    }

    public java.util.List<String> listAiProviders() {
        return aiServiceFactory.listProviders();
    }
}