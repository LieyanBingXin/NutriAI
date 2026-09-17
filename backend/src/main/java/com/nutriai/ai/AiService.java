package com.nutriai.ai;

import com.nutriai.dto.RecommendationResponse;
import com.nutriai.dto.UserProfileRequest;

public interface AiService {

    RecommendationResponse analyzeAndRecommend(UserProfileRequest profile);

    String getProviderName();

    default boolean isAvailable() {
        return true;
    }
}