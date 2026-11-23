package com.nutriai.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class FoodDiaryDto {

    private Long id;
    private String foodName;
    private String mealType;
    private Double amountGram;
    private Double calories;
    private Double protein;
    private Double carbs;
    private Double fat;
    private LocalDate recordDate;
    private String note;
    private LocalDateTime createdAt;

    public static FoodDiaryDto from(com.nutriai.entity.FoodDiary fd) {
        return new FoodDiaryDto(
                fd.getId(),
                fd.getFoodName(),
                normalizeMealType(fd.getMealType()),
                fd.getAmountGram(),
                fd.getCalories(),
                fd.getProtein(),
                fd.getCarbs(),
                fd.getFat(),
                fd.getRecordDate(),
                fd.getNote(),
                fd.getCreatedAt()
        );
    }

    /** 餐别统一成中文：兼容 AI 返回的 breakfast/lunch/dinner/snack 与历史脏数据 */
    public static String normalizeMealType(String raw) {
        if (raw == null || raw.isBlank()) return "午餐";
        String s = raw.trim().toLowerCase();
        if (s.contains("早") || s.contains("breakfast")) return "早餐";
        if (s.contains("午") || s.contains("lunch")) return "午餐";
        if (s.contains("晚") || s.contains("dinner")) return "晚餐";
        if (s.contains("加餐") || s.contains("snack") || s.contains("夜宵") || s.contains("宵夜")) return "加餐";
        return "午餐";
    }
}