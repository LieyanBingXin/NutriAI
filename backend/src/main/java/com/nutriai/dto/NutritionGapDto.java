package com.nutriai.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 营养缺口分析结果 —— 「AI 教练」缺口雷达
 */
@Data
public class NutritionGapDto {

    private int trackedDays;               // 近7日有记录的天数

    private Double targetCalories;
    private Double targetProtein;
    private Double targetCarbs;
    private Double targetFat;

    private Double avgCalories;
    private Double avgProtein;
    private Double avgCarbs;
    private Double avgFat;

    private Double calPercent;
    private Double proteinPercent;
    private Double carbsPercent;
    private Double fatPercent;

    private List<GapItem> deficiencies = new ArrayList<>();
    private List<GapItem> surpluses = new ArrayList<>();

    private String aiInsight;

    @Data
    public static class GapItem {
        private String nutrient;    // 热量 / 蛋白质 / 碳水 / 脂肪
        private String issue;       // 偏低 / 偏高
        private double percent;     // 实际占目标百分比
        private String suggestion;  // 给出的建议
    }
}