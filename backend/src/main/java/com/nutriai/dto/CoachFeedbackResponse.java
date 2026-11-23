package com.nutriai.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

/**
 * 「AI 教练」实时反馈响应 —— 针对今日已摄入给出剩余配额建议
 */
@Data
public class CoachFeedbackResponse {

    private String message;

    /** 今日剩余/超额估算（如 “今日还可摄入约 480 kcal”） */
    private String remainingSummary;

    /** 接下来的快速建议（3-5 条） */
    private List<String> quickSuggestions;

    /** 今日已摄入汇总（结构化，供前端配额盘展示） */
    private TodayIntake today;

    @Data
    public static class TodayIntake {
        private Double calories;
        private Double protein;
        private Double carbs;
        private Double fat;
        private Double caloriePercent;
    }
}