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

    public RecommendationResponse getRecommendations(UserProfileRequest profile) {
        log.info("开始生成推荐, user={}, age={}", profile.getGender(), profile.getAge());

        AiService ai = aiServiceFactory.getService();
        log.info("使用 AI provider: {}", ai.getProviderName());

        RecommendationResponse response = ai.analyzeAndRecommend(profile);

        log.info("推荐生成完成, 食谱数量={}", response.getRecipes() == null ? 0 : response.getRecipes().size());
        return response;
    }

    public java.util.List<String> listAiProviders() {
        return aiServiceFactory.listProviders();
    }
}