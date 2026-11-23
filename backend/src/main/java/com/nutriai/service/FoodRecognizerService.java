package com.nutriai.service;

import com.nutriai.ai.AiServiceFactory;
import com.nutriai.dto.FoodRecognitionResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class FoodRecognizerService {

    private final RagService ragService;
    private final AiServiceFactory aiServiceFactory;

    public FoodRecognitionResult recognize(String description) {
        Map<String, Object> local = ragService.tryLocalRecognize(description);
        if (local != null) {
            log.info("[FoodRec] local-db hit: {} -> {}", description, local.get("foodName"));
            FoodRecognitionResult result = new FoodRecognitionResult();
            result.setFoodName((String) local.get("foodName"));
            result.setMealType((String) local.get("mealType"));
            result.setCalories(toDouble(local.get("calories")));
            result.setProteinGrams(toDouble(local.get("proteinGrams")));
            result.setCarbsGrams(toDouble(local.get("carbsGrams")));
            result.setFatGrams(toDouble(local.get("fatGrams")));
            result.setServingSize(toDouble(local.get("servingSize")));
            return result;
        }
        log.info("[FoodRec] local-db miss, falling back to AI: {}", description);
        return aiServiceFactory.getService().recognizeFood(description);
    }

    private Double toDouble(Object v) {
        if (v instanceof Number n) return n.doubleValue();
        return 0.0;
    }
}