package com.nutriai.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class FoodRecognitionResult {
    private String foodName;
    private String mealType;
    private Double calories;
    private Double proteinGrams;
    private Double carbsGrams;
    private Double fatGrams;
    private Double servingSize;
}