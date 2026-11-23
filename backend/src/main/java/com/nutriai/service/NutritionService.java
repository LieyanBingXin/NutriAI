package com.nutriai.service;

import com.nutriai.ai.AiService;
import com.nutriai.ai.AiServiceFactory;
import com.nutriai.dto.DailyNutritionDto;
import com.nutriai.dto.FoodDiaryDto;
import com.nutriai.dto.RecommendationResponse;
import com.nutriai.dto.UserProfileRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.time.LocalDate;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class NutritionService {

    private final AiServiceFactory aiServiceFactory;
    private final AuthService authService;
    private final HistoryService historyService;
    private final FoodDiaryService foodDiaryService;
    private final FoodPreferenceService foodPreferenceService;

    public RecommendationResponse getRecommendations(UserProfileRequest profile) {
        return getRecommendations(profile, null, true);
    }

    public RecommendationResponse getRecommendations(UserProfileRequest profile, Long userId, boolean usePreferences) {
        return getRecommendations(profile, userId, usePreferences, null);
    }

    public RecommendationResponse getRecommendations(UserProfileRequest profile, Long userId, boolean usePreferences, String extraNotes) {
        log.info("开始生成推荐, gender={}, age={}, goal={}", profile.getGender(), profile.getAge(), profile.getHealthGoal());

        String foodDiaryContext = buildFoodDiaryContext(userId);
        String preferenceContext = usePreferences ? buildPreferenceContext(userId) : "";
        StringBuilder ctx = new StringBuilder();
        ctx.append(foodDiaryContext).append(preferenceContext);
        if (extraNotes != null && !extraNotes.isBlank()) {
            ctx.append("\n\n【会话强调·请严格遵守】").append(extraNotes);
        }
        AiService ai = aiServiceFactory.getService();
        log.info("使用 AI provider: {}, usePreferences={}", ai.getProviderName(), usePreferences);

        RecommendationResponse response = ai.analyzeAndRecommend(profile, ctx.toString());

        log.info("推荐生成完成, 食谱数量={}", response.getRecipes() == null ? 0 : response.getRecipes().size());

        if (userId != null) {
            try {
                historyService.saveRecord(userId, profile, response, response.getAiProvider());
                log.info("推荐已保存到用户历史, userId={}", userId);
            } catch (Exception e) {
                log.warn("保存历史记录失败（不影响推荐结果）: {}", e.getMessage());
            }
        }

        return response;
    }

    public Flux<String> streamRecommendations(UserProfileRequest profile) {
        AiService ai = aiServiceFactory.getService();
        log.info("流式推荐, provider={}", ai.getProviderName());
        return ai.streamAndRecommend(profile, "");
    }

    public Flux<String> streamRecommendations(UserProfileRequest profile, Long userId, boolean usePreferences) {
        String foodDiaryContext = buildFoodDiaryContext(userId);
        String preferenceContext = usePreferences ? buildPreferenceContext(userId) : "";
        AiService ai = aiServiceFactory.getService();
        log.info("流式推荐(含饮食上下文), provider={}, usePreferences={}", ai.getProviderName(), usePreferences);
        return ai.streamAndRecommend(profile, foodDiaryContext + preferenceContext);
    }

    public java.util.List<String> listAiProviders() {
        return aiServiceFactory.listProviders();
    }

    private String buildFoodDiaryContext(Long userId) {
        if (userId == null) return "";
        try {
            List<DailyNutritionDto> weekly = foodDiaryService.getWeeklySummary(userId, LocalDate.now());
            if (weekly == null || weekly.isEmpty()) return "";

            StringBuilder sb = new StringBuilder();
            sb.append("\n\n【用户近7日饮食记录】\n");
            int totalDays = 0;
            double totalCal = 0, totalPro = 0, totalCarbs = 0, totalFat = 0;
            double totalCalGoal = 0, totalProGoal = 0, totalCarbsGoal = 0, totalFatGoal = 0;
            int goalDays = 0;

            for (DailyNutritionDto d : weekly) {
                if (d.getTotalItems() > 0) {
                    totalDays++;
                    totalCal += d.getTotalCalories() != null ? d.getTotalCalories() : 0;
                    totalPro += d.getTotalProtein() != null ? d.getTotalProtein() : 0;
                    totalCarbs += d.getTotalCarbs() != null ? d.getTotalCarbs() : 0;
                    totalFat += d.getTotalFat() != null ? d.getTotalFat() : 0;
                }
                if (d.getCalorieGoal() != null) {
                    totalCalGoal += d.getCalorieGoal();
                    totalProGoal += d.getProteinGoal() != null ? d.getProteinGoal() : 0;
                    totalCarbsGoal += d.getCarbsGoal() != null ? d.getCarbsGoal() : 0;
                    totalFatGoal += d.getFatGoal() != null ? d.getFatGoal() : 0;
                    goalDays++;
                }
            }

            if (totalDays == 0) return "";

            double avgCal = totalCal / totalDays;
            double avgPro = totalPro / totalDays;
            double avgCarbs = totalCarbs / totalDays;
            double avgFat = totalFat / totalDays;

            sb.append(String.format("- 有记录的 %d 天中，日均摄入约 %.0f kcal · 蛋白质 %.0f g · 碳水 %.0f g · 脂肪 %.0f g\n",
                    totalDays, avgCal, avgPro, avgCarbs, avgFat));

            if (goalDays > 0) {
                double avgCalGoal = totalCalGoal / goalDays;
                double avgProGoal = totalProGoal / goalDays;
                double avgCarbsGoal = totalCarbsGoal / goalDays;
                double avgFatGoal = totalFatGoal / goalDays;
                double calRate = avgCalGoal > 0 ? Math.round(avgCal / avgCalGoal * 100) : 0;
                double proRate = avgProGoal > 0 ? Math.round(avgPro / avgProGoal * 100) : 0;
                double carbsRate = avgCarbsGoal > 0 ? Math.round(avgCarbs / avgCarbsGoal * 100) : 0;
                double fatRate = avgFatGoal > 0 ? Math.round(avgFat / avgFatGoal * 100) : 0;
                sb.append(String.format("- 目标值：%.0f kcal · 蛋白质 %.0f g · 碳水 %.0f g · 脂肪 %.0f g\n",
                        avgCalGoal, avgProGoal, avgCarbsGoal, avgFatGoal));
                sb.append(String.format("- 达标率：热量 %d%% · 蛋白质 %d%% · 碳水 %d%% · 脂肪 %d%%\n",
                        calRate, proRate, carbsRate, fatRate));
            }

            sb.append("请根据以上实际饮食数据，分析用户当前饮食存在的问题，并在建议中针对性地提出改进方案。");
            return sb.toString();
        } catch (Exception e) {
            log.warn("获取食物日记上下文失败: {}", e.getMessage());
            return "";
        }
    }

    private String buildPreferenceContext(Long userId) {
        if (userId == null) return "";
        try {
            return foodPreferenceService.buildPreferenceContext(userId);
        } catch (Exception e) {
            log.warn("获取食物偏好上下文失败: {}", e.getMessage());
            return "";
        }
    }
}