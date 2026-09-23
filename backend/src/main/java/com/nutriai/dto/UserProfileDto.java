package com.nutriai.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class UserProfileDto {

    private Long id;
    private String gender;
    private Integer age;
    private Double heightCm;
    private Double weightKg;
    private String activityLevel;
    private String healthGoal;
    private String tastePreference;
    private List<String> foodPreferences;
    private List<String> foodAllergies;
    private LocalDateTime updatedAt;
}