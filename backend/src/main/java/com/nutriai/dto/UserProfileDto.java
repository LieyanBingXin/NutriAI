package com.nutriai.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class UserProfileDto {

    private Long id;

    private String gender;

    @Min(value = 1, message = "年龄必须在 1-150 之间")
    @Max(value = 150, message = "年龄必须在 1-150 之间")
    private Integer age;

    @DecimalMin(value = "30", message = "身高不合法")
    @DecimalMax(value = "250", message = "身高不合法")
    private Double heightCm;

    @DecimalMin(value = "2", message = "体重不合法")
    @DecimalMax(value = "300", message = "体重不合法")
    private Double weightKg;

    private String activityLevel;
    private String healthGoal;
    private List<String> tastePreferences;

    private String region;

    @DecimalMin(value = "0", message = "热量目标不能为负")
    @DecimalMax(value = "8000", message = "热量目标过大")
    private Double dailyCalorieGoal;

    @DecimalMin(value = "0", message = "蛋白质目标不能为负")
    @DecimalMax(value = "500", message = "蛋白质目标过大")
    private Double proteinGoal;

    @DecimalMin(value = "0", message = "碳水目标不能为负")
    @DecimalMax(value = "1000", message = "碳水目标过大")
    private Double carbsGoal;

    @DecimalMin(value = "0", message = "脂肪目标不能为负")
    @DecimalMax(value = "300", message = "脂肪目标过大")
    private Double fatGoal;

    private List<String> foodPreferences;
    private List<String> foodAllergies;
    private List<String> healthConditions;
    private LocalDateTime updatedAt;
}