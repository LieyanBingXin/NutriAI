package com.nutriai.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.time.LocalDate;

@Data
public class FoodDiaryRequest {

    @NotBlank(message = "食物名称不能为空")
    private String foodName;

    private String mealType;

    private Double amountGram;

    private Double calories;

    private Double protein;

    private Double carbs;

    private Double fat;

    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate recordDate;

    private String note;
}