package com.nutriai.dto;

import lombok.Data;

import java.util.List;

@Data
public class RecommendationResponse {

    private String summary;

    private NutritionalAdvice nutritionalAdvice;

    private List<RecipeDto> recipes;

    private List<String> generalTips;

    private String aiProvider;

    private long generatedAt;

    @Data
    public static class NutritionalAdvice {
        private Double dailyCalories;
        private Double proteinGrams;
        private Double carbsGrams;
        private Double fatGrams;
        private String bmiCategory;
        private String metabolismNote;
    }

    @Data
    public static class RecipeDto {
        private String name;
        private String description;
        private List<String> ingredients;
        private List<String> steps;
        private NutritionalInfo nutritionalInfo;
        private String estimatedTime;
        private String difficulty;
        private List<String> tags;
    }

    @Data
    public static class NutritionalInfo {
        private Double calories;
        private Double protein;
        private Double carbs;
        private Double fat;
        private Double fiber;
        private Double sodium;
    }
}