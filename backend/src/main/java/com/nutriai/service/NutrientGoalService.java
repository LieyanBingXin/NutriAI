package com.nutriai.service;

import com.nutriai.dto.NutrientGoalDto;
import com.nutriai.entity.UserProfile;
import com.nutriai.repository.UserProfileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class NutrientGoalService {

    private final UserProfileRepository profileRepository;

    // 蛋白质推荐系数 (g/kg 体重)
    private static final Map<String, double[]> PROTEIN_RANGE = Map.of(
            "LOSE_WEIGHT", new double[]{1.6, 2.0},
            "MAINTAIN", new double[]{0.8, 1.2},
            "GAIN_WEIGHT", new double[]{1.2, 1.6},
            "BUILD_MUSCLE", new double[]{1.8, 2.2},
            "IMPROVE_HEALTH", new double[]{1.0, 1.4}
    );

    // 脂肪占比范围 (占总热量%)
    private static final Map<String, double[]> FAT_RANGE = Map.of(
            "LOSE_WEIGHT", new double[]{20, 25},
            "MAINTAIN", new double[]{25, 30},
            "GAIN_WEIGHT", new double[]{25, 35},
            "BUILD_MUSCLE", new double[]{20, 25},
            "IMPROVE_HEALTH", new double[]{25, 30}
    );

    // 活动量系数
    private static final Map<String, Double> ACTIVITY_MULTIPLIER = new LinkedHashMap<>();
    static {
        ACTIVITY_MULTIPLIER.put("SEDENTARY", 1.2);
        ACTIVITY_MULTIPLIER.put("LIGHT", 1.375);
        ACTIVITY_MULTIPLIER.put("MODERATE", 1.55);
        ACTIVITY_MULTIPLIER.put("ACTIVE", 1.725);
        ACTIVITY_MULTIPLIER.put("VERY_ACTIVE", 1.9);
    }

    // 目标热量调整 (kcal/天)
    private static final Map<String, Double> GOAL_ADJUSTMENT = Map.of(
            "LOSE_WEIGHT", -500.0,
            "MAINTAIN", 0.0,
            "GAIN_WEIGHT", 300.0,
            "BUILD_MUSCLE", 200.0,
            "IMPROVE_HEALTH", 0.0
    );

    public NutrientGoalDto calculate(String gender, int age, double heightCm, double weightKg,
                                      String activityLevel, String healthGoal) {
        // 1. Mifflin-St Jeor 公式计算 BMR
        double bmr;
        if ("female".equalsIgnoreCase(gender) || "女".equals(gender) || "FEMALE".equalsIgnoreCase(gender)) {
            bmr = 10 * weightKg + 6.25 * heightCm - 5 * age - 161;
        } else {
            bmr = 10 * weightKg + 6.25 * heightCm - 5 * age + 5;
        }

        // 2. 活动量 → TDEE
        double activityMult = ACTIVITY_MULTIPLIER.getOrDefault(
                activityLevel != null ? activityLevel.toUpperCase() : "MODERATE", 1.55);
        double tdee = bmr * activityMult;

        // 3. 目标调整
        double adj = GOAL_ADJUSTMENT.getOrDefault(
                healthGoal != null ? healthGoal.toUpperCase() : "MAINTAIN", 0.0);
        double targetCal = tdee + adj;

        // 4. 蛋白质 g/kg
        double[] protRange = PROTEIN_RANGE.getOrDefault(
                healthGoal != null ? healthGoal.toUpperCase() : "MAINTAIN", new double[]{0.8, 1.2});
        double proteinG = Math.round(weightKg * ((protRange[0] + protRange[1]) / 2.0) * 10) / 10.0;

        // 5. 脂肪
        double[] fatRange = FAT_RANGE.getOrDefault(
                healthGoal != null ? healthGoal.toUpperCase() : "MAINTAIN", new double[]{25, 30});
        double fatPct = (fatRange[0] + fatRange[1]) / 2.0;
        double fatG = Math.round(targetCal * fatPct / 100.0 / 9.0 * 10) / 10.0;

        // 6. 碳水 = 剩余热量
        double proteinCal = proteinG * 4;
        double fatCal = fatG * 9;
        double carbsCal = targetCal - proteinCal - fatCal;
        double carbsG = Math.round(Math.max(0, carbsCal / 4.0) * 10) / 10.0;

        NutrientGoalDto dto = new NutrientGoalDto();
        dto.setBmr(Math.round(bmr));
        dto.setTdee(Math.round(tdee));
        dto.setRecommendedCalories(Math.round(targetCal));
        dto.setRecommendedProtein(proteinG);
        dto.setRecommendedCarbs(carbsG);
        dto.setRecommendedFat(fatG);
        dto.setProteinPerKg(weightKg > 0 ? Math.round(proteinG / weightKg * 10) / 10.0 : 0);
        dto.setBmi(heightCm > 0
                ? Math.round(weightKg / ((heightCm / 100) * (heightCm / 100)) * 10) / 10.0
                : 0);
        dto.setFormula("Mifflin-St Jeor");

        // 生成说明文本
        dto.setExplanation(buildExplanation(healthGoal, targetCal, proteinG, carbsG, fatG, bmr, weightKg));

        return dto;
    }

    public NutrientGoalDto calculateFromProfile(Long userId) {
        UserProfile p = profileRepository.findByUserId(userId).orElse(null);
        if (p == null) return null;
        return calculate(
                p.getGender(), p.getAge() != null ? p.getAge() : 30,
                p.getHeightCm() != null ? p.getHeightCm() : 170,
                p.getWeightKg() != null ? p.getWeightKg() : 65,
                p.getActivityLevel(), p.getHealthGoal()
        );
    }

    private String buildExplanation(String healthGoal, double cals, double protein, double carbs,
                                    double fat, double bmr, double weightKg) {
        String goalLabel = switch (healthGoal != null ? healthGoal.toUpperCase() : "") {
            case "LOSE_WEIGHT" -> "减脂";
            case "GAIN_WEIGHT" -> "增重";
            case "BUILD_MUSCLE" -> "增肌";
            default -> "维持健康";
        };
        return String.format(
                "基于 Mifflin-St Jeor 公式，你的基础代谢约 **%d kcal/天**。" +
                "考虑到你的活动水平和「%s」目标，推荐每日摄入约 **%d kcal**。" +
                "蛋白质 **%.0f g**（约 %.1f g/kg）保证肌肉维持，" +
                "碳水 **%.0f g** 提供主要能量来源，" +
                "脂肪 **%.0f g** 满足必需脂肪酸需求。",
                Math.round(bmr), goalLabel, Math.round(cals),
                protein, weightKg > 0 ? protein / weightKg : 0,
                carbs, fat
        );
    }
}