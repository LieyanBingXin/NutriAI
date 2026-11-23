package com.nutriai.dto;

import lombok.Data;

@Data
public class NutrientGoalDto {
    private double bmr;
    private double tdee;
    private double bmi;
    private String formula;
    private long recommendedCalories;
    private double recommendedProtein;
    private double recommendedCarbs;
    private double recommendedFat;
    private double proteinPerKg;
    private String explanation;
}