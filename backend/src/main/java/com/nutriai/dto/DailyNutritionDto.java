package com.nutriai.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class DailyNutritionDto {

    private String date;
    private int totalItems;

    private Double totalCalories;
    private Double totalProtein;
    private Double totalCarbs;
    private Double totalFat;
    private Double avgCaloriesPerMeal;

    private Double calorieGoal;
    private Double proteinGoal;
    private Double carbsGoal;
    private Double fatGoal;

    private Double caloriePercent;
    private Double proteinPercent;
    private Double carbsPercent;
    private Double fatPercent;

    private List<FoodDiaryDto> items;

    public DailyNutritionDto(String date, int totalItems,
                             Double totalCalories, Double totalProtein,
                             Double totalCarbs, Double totalFat,
                             Double avgCaloriesPerMeal,
                             List<FoodDiaryDto> items) {
        this.date = date;
        this.totalItems = totalItems;
        this.totalCalories = totalCalories;
        this.totalProtein = totalProtein;
        this.totalCarbs = totalCarbs;
        this.totalFat = totalFat;
        this.avgCaloriesPerMeal = avgCaloriesPerMeal;
        this.items = items;
    }
}