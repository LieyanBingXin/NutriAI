package com.nutriai.ai;

import com.nutriai.config.NutriAiProperties;
import com.nutriai.dto.RecommendationResponse;
import com.nutriai.dto.UserProfileRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class MockAiService implements AiService {

    private final NutriAiProperties properties;

    @Override
    public RecommendationResponse analyzeAndRecommend(UserProfileRequest profile) {
        log.info("[MockAi] 收到分析请求: gender={}, age={}, height={}, weight={}",
                profile.getGender(), profile.getAge(),
                profile.getHeightCm(), profile.getWeightKg());

        RecommendationResponse response = new RecommendationResponse();
        response.setAiProvider("mock");
        response.setGeneratedAt(System.currentTimeMillis());
        response.setSummary(buildSummary(profile));
        response.setNutritionalAdvice(calcNutrition(profile));
        response.setRecipes(generateMockRecipes(profile));
        response.setGeneralTips(generateTips(profile));

        return response;
    }

    @Override
    public String getProviderName() {
        return "mock";
    }

    @Override
    public boolean isAvailable() {
        return true;
    }

    private String buildSummary(UserProfileRequest p) {
        double bmi = calcBmi(p.getHeightCm(), p.getWeightKg());
        return String.format(
                "基于您的信息（%d岁，BMI %.1f，目标：%s），为您生成了个性化饮食建议。",
                p.getAge(), bmi, p.getHealthGoal().name()
        );
    }

    private RecommendationResponse.NutritionalAdvice calcNutrition(UserProfileRequest p) {
        RecommendationResponse.NutritionalAdvice advice = new RecommendationResponse.NutritionalAdvice();

        double bmi = calcBmi(p.getHeightCm(), p.getWeightKg());
        double baseCalories = calcBmr(p);
        double activityMultiplier = switch (p.getActivityLevel()) {
            case SEDENTARY -> 1.2;
            case LIGHT -> 1.375;
            case MODERATE -> 1.55;
            case ACTIVE -> 1.725;
            case VERY_ACTIVE -> 1.9;
        };
        double dailyCal = baseCalories * activityMultiplier;

        advice.setDailyCalories(Math.round(dailyCal * 10) / 10.0);
        advice.setProteinGrams(Math.round(p.getWeightKg() * 1.6 * 10) / 10.0);
        advice.setCarbsGrams(Math.round(dailyCal * 0.45 / 4 * 10) / 10.0);
        advice.setFatGrams(Math.round(dailyCal * 0.30 / 9 * 10) / 10.0);
        advice.setBmiCategory(classifyBmi(bmi));
        advice.setMetabolismNote("基础代谢估算: " + Math.round(baseCalories) + " kcal/天");

        return advice;
    }

    private double calcBmi(double heightCm, double weightKg) {
        double heightM = heightCm / 100.0;
        return weightKg / (heightM * heightM);
    }

    private String classifyBmi(double bmi) {
        if (bmi < 18.5) return "偏瘦";
        if (bmi < 24) return "正常";
        if (bmi < 28) return "偏胖";
        return "肥胖";
    }

    private double calcBmr(UserProfileRequest p) {
        double base = 10 * p.getWeightKg() + 6.25 * p.getHeightCm() - 5 * p.getAge();
        return switch (p.getGender()) {
            case MALE -> base + 5;
            case FEMALE -> base - 161;
            default -> base - 78;
        };
    }

    private List<RecommendationResponse.RecipeDto> generateMockRecipes(UserProfileRequest p) {
        RecommendationResponse.RecipeDto r1 = new RecommendationResponse.RecipeDto();
        r1.setName("鸡胸肉蔬菜沙拉");
        r1.setDescription("低卡高蛋白，适合减脂期食用");
        r1.setIngredients(List.of("鸡胸肉 150g", "生菜 200g", "番茄 1个", "黄瓜 1根", "橄榄油 1勺", "柠檬汁 适量"));
        r1.setSteps(List.of(
                "鸡胸肉煮熟撕成丝",
                "蔬菜洗净切好",
                "混合后淋上橄榄油和柠檬汁"
        ));
        r1.setEstimatedTime("15分钟");
        r1.setDifficulty("简单");
        r1.setTags(List.of("低卡", "高蛋白", "快手"));

        RecommendationResponse.NutritionalInfo n1 = new RecommendationResponse.NutritionalInfo();
        n1.setCalories(280.0); n1.setProtein(32.0); n1.setCarbs(12.0); n1.setFat(14.0);
        r1.setNutritionalInfo(n1);

        RecommendationResponse.RecipeDto r2 = new RecommendationResponse.RecipeDto();
        r2.setName("燕麦牛奶粥");
        r2.setDescription("早餐优选，提供持久能量");
        r2.setIngredients(List.of("燕麦片 50g", "牛奶 250ml", "鸡蛋 1个", "蓝莓 适量", "蜂蜜 少量"));
        r2.setSteps(List.of(
                "燕麦与牛奶同煮至浓稠",
                "打入鸡蛋搅匀",
                "出锅后加蓝莓和蜂蜜"
        ));
        r2.setEstimatedTime("10分钟");
        r2.setDifficulty("简单");
        r2.setTags(List.of("早餐", "高纤维", "快手"));

        RecommendationResponse.NutritionalInfo n2 = new RecommendationResponse.NutritionalInfo();
        n2.setCalories(380.0); n2.setProtein(18.0); n2.setCarbs(52.0); n2.setFat(10.0);
        r2.setNutritionalInfo(n2);

        return List.of(r1, r2);
    }

    private List<String> generateTips(UserProfileRequest p) {
        return List.of(
                "每天保持 8 杯水的摄入",
                "建议三餐定时，避免暴饮暴食",
                "加工食品和含糖饮料应尽量减少",
                "每周至少 150 分钟中等强度运动",
                "保持充足睡眠有助于代谢"
        );
    }
}