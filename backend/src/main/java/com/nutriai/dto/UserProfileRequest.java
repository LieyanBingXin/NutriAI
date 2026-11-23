package com.nutriai.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

@Data
public class UserProfileRequest {

    @NotNull(message = "性别不能为空")
    private Gender gender;

    @NotNull(message = "年龄不能为空")
    @Min(value = 1, message = "年龄必须大于0")
    @Max(value = 150, message = "年龄不合法")
    private Integer age;

    @NotNull(message = "身高不能为空")
    @Min(value = 30, message = "身高必须大于30cm")
    @Max(value = 250, message = "身高不合法")
    private Double heightCm;

    @NotNull(message = "体重不能为空")
    @Min(value = 2, message = "体重必须大于2kg")
    @Max(value = 300, message = "体重不合法")
    private Double weightKg;

    private ActivityLevel activityLevel = ActivityLevel.MODERATE;

    private HealthGoal healthGoal = HealthGoal.MAINTAIN;

    private List<String> foodPreferences;

    private List<String> foodAllergies;

    private List<TastePreference> tastePreferences;

    private String region;

    private List<String> additionalNotes;

    public enum Gender {
        MALE, FEMALE, OTHER
    }

    public enum ActivityLevel {
        SEDENTARY, LIGHT, MODERATE, ACTIVE, VERY_ACTIVE
    }

    public enum HealthGoal {
        LOSE_WEIGHT, MAINTAIN, GAIN_WEIGHT, BUILD_MUSCLE, IMPROVE_HEALTH
    }

    public enum TastePreference {
        LIGHT, SALTY, SPICY, SWEET, SOUR, UMAMI, MIXED
    }
}