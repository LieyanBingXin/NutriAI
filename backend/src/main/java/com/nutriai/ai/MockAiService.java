package com.nutriai.ai;

import com.nutriai.config.NutriAiProperties;
import com.nutriai.dto.FoodRecognitionResult;
import com.nutriai.dto.NutritionGapDto;
import com.nutriai.dto.RecommendationResponse;
import com.nutriai.dto.UserProfileRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.time.Duration;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class MockAiService implements AiService {

    private final NutriAiProperties properties;

    @Override
    public RecommendationResponse analyzeAndRecommend(UserProfileRequest profile, String foodDiaryContext) {
        log.info("[MockAi] 收到分析请求: gender={}, age={}, height={}, weight={}, hasDiary={}",
                profile.getGender(), profile.getAge(),
                profile.getHeightCm(), profile.getWeightKg(),
                foodDiaryContext != null && !foodDiaryContext.isEmpty());

        RecommendationResponse response = new RecommendationResponse();
        response.setAiProvider("mock");
        response.setGeneratedAt(System.currentTimeMillis());
        response.setSummary(buildSummary(profile, foodDiaryContext));
        response.setNutritionalAdvice(calcNutrition(profile));
        response.setRecipes(generateMockRecipes(profile));
        response.setGeneralTips(generateTips(profile, foodDiaryContext));

        return response;
    }

    @Override
    public Flux<String> streamAndRecommend(UserProfileRequest profile, String foodDiaryContext) {
        String text = buildStreamText(profile, foodDiaryContext);
        String[] tokens = text.split("(?<=。|！|？|，|;|；|\\n)");
        return Flux.fromArray(tokens)
                .flatMap(s -> Flux.just(s).delayElements(Duration.ofMillis(40)));
    }

    @Override
    public String getProviderName() {
        return "mock";
    }

    @Override
    public boolean isAvailable() {
        return true;
    }

    @Override
    public FoodRecognitionResult recognizeFood(String description) {
        log.info("[MockAi] 食物识别: {}", description);
        FoodRecognitionResult result = new FoodRecognitionResult();
        result.setFoodName(description);
        if (description.contains("饭") || description.contains("米")) {
            result.setCalories(260.0);
            result.setProteinGrams(5.0);
            result.setCarbsGrams(55.0);
            result.setFatGrams(1.5);
            result.setServingSize(200.0);
            result.setMealType("lunch");
        } else if (description.contains("肉") || description.contains("鸡")) {
            result.setCalories(220.0);
            result.setProteinGrams(35.0);
            result.setCarbsGrams(2.0);
            result.setFatGrams(8.0);
            result.setServingSize(150.0);
            result.setMealType("lunch");
        } else {
            result.setCalories(150.0);
            result.setProteinGrams(8.0);
            result.setCarbsGrams(15.0);
            result.setFatGrams(6.0);
            result.setServingSize(200.0);
            result.setMealType("snack");
        }
        return result;
    }

    @Override
    public NutritionGapDto interpretGap(NutritionGapDto gap) {
        if (gap == null) return null;
        StringBuilder sb = new StringBuilder();
        if (gap.getDeficiencies() != null && !gap.getDeficiencies().isEmpty()) {
            sb.append("偏低：");
            sb.append(String.join("、", gap.getDeficiencies().stream()
                    .map(d -> d.getNutrient() + " " + Math.round(d.getPercent()) + "%")
                    .toList()));
        }
        if (gap.getSurpluses() != null && !gap.getSurpluses().isEmpty()) {
            if (sb.length() > 0) sb.append("；");
            sb.append("偏高：");
            sb.append(String.join("、", gap.getSurpluses().stream()
                    .map(d -> d.getNutrient() + " " + Math.round(d.getPercent()) + "%")
                    .toList()));
        }
        if (sb.length() == 0) {
            gap.setAiInsight("近 7 日的营养摄入整体均衡，继续保持即可。");
        } else {
            gap.setAiInsight("离线营养分析 —— " + sb + "。优先补齐偏低项，同时控制偏高项。");
        }
        return gap;
    }

    @Override
    public String chatWithContext(String systemPrompt, String userContent) {
        // 不要在离线模式下回显拼装后的上下文（包含画像/偏好/日记等隐私数据）
        return "🤖 现在是离线演示模式（未配置 AI_API_KEY），自由问答能力有限；"
                + "不过下面这些指令仍然可以直接用：\n"
                + "• 「体重 68.2kg」记录体重\n"
                + "• 「早餐吃了鸡蛋和牛奶」记录饮食\n"
                + "• 「我家有鸡胸肉、西兰花」记住冰箱食材\n"
                + "• 「今晚吃什么」生成离线食谱推荐\n"
                + "• 「今天缺口怎么样」查看营养缺口";
    }

    @Override
    public String generateCoachFeedback(UserProfileRequest profile, String context) {
        String ctx = context == null ? "" : context.trim();
        if (ctx.isEmpty()) {
            return "先记录今天吃了什么，我就能根据剩余配额给你具体建议。";
        }
        return "离线教练建议：\n" + ctx
                + "\n接下来优先补充优质蛋白和蔬菜，主食选择全谷物，并留意饮水量。";
    }

    @Override
    public RecommendationResponse recommendFromFridge(UserProfileRequest profile, List<String> ingredients, String context) {
        RecommendationResponse response = analyzeAndRecommend(profile, context);
        if (ingredients != null && !ingredients.isEmpty()) {
            String list = String.join("、", ingredients);
            response.setSummary("（离线模式）用现有食材 " + list + " 搭配出 2 道家常菜。" + response.getSummary());
        }
        return response;
    }

    private String buildStreamText(UserProfileRequest p, String foodDiaryContext) {
        double bmi = calcBmi(p.getHeightCm(), p.getWeightKg());
        double baseCalories = calcBmr(p);
        double multiplier = switch (p.getActivityLevel()) {
            case SEDENTARY -> 1.2;
            case LIGHT -> 1.375;
            case MODERATE -> 1.55;
            case ACTIVE -> 1.725;
            case VERY_ACTIVE -> 1.9;
        };
        double daily = baseCalories * multiplier;

        String region = p.getRegion() != null && !p.getRegion().isBlank() ? p.getRegion() : "中国大陆";
        String regionRecipe = getRegionRecipe(region);

        String diarySection = "";
        if (foodDiaryContext != null && !foodDiaryContext.isEmpty()) {
            diarySection = """

                    ## 近期饮食分析

                    根据您近7天的饮食记录，我注意到以下几点：
                    - 您的食物日记数据已纳入分析，请参考下方达标率数据
                    - 建议关注三大营养素的配比是否合理
                    - 如果某类营养素长期不足或超标，需要在后续饮食中调整
                    """;
        } else {
            diarySection = """

                    ## 近期饮食分析

                    您还没有记录近期的饮食情况。建议使用食物日记功能记录每日摄入，这样我可以为您提供更精准的分析和建议。
                    """;
        }

        return String.format("""
                ## 用户健康评估

                好的，让我为您分析一下身体状况。
                您是一名 %d 岁的 %s，身高 %.1f 厘米，体重 %.1f 千克。
                计算得出您的 BMI 为 %.1f，属于%s范围。
                根据 Mifflin-St Jeor 公式，您的基础代谢率约为 %.0f kcal/天。
                考虑您的 %s 活动水平，每日总能量消耗约为 %.0f kcal。

                根据您的 %s 目标，我为您制定了以下营养建议：
                每日建议摄入约 %.0f 千卡热量。
                蛋白质约 %.0f 克（%.1f g/kg），碳水化合物约 %.0f 克，脂肪约 %.0f 克。
                %s
                ## 推荐食谱（结合%s特色）

                ### 食谱一：%s
                - 食材：%s，%s，%s，%s
                - 做法：
                  1. %s
                  2. %s
                  3. %s
                - 热量：约 %.0f kcal | 蛋白质：%.0f g | 碳水：%.0f g | 脂肪：%.0f g

                ### 食谱二：%s
                - 食材：%s，%s，%s，%s
                - 做法：
                  1. %s
                  2. %s
                  3. %s
                - 热量：约 %.0f kcal | 蛋白质：%.0f g | 碳水：%.0f g | 脂肪：%.0f g

                ## 个性化饮食建议

                - 每天保持 8 杯水的摄入，三餐定时定量
                - 加工食品和含糖饮料应尽量减少
                - 每周至少 150 分钟中等强度运动
                - 保持充足睡眠有助于代谢和体重管理
                - 建议使用食物日记持续追踪饮食，AI 可据此给出更精准建议

                以上建议仅供参考，具体饮食方案建议咨询专业医生或注册营养师。祝您健康！
                """,
                p.getAge(),
                "MALE".equals(String.valueOf(p.getGender())) ? "男性" : "FEMALE".equals(String.valueOf(p.getGender())) ? "女性" : "用户",
                p.getHeightCm(), p.getWeightKg(),
                bmi, classifyBmi(bmi),
                baseCalories,
                p.getActivityLevel(),
                daily,
                "LOSE_WEIGHT".equals(String.valueOf(p.getHealthGoal())) ? "减脂" :
                        "GAIN_WEIGHT".equals(String.valueOf(p.getHealthGoal())) ? "增重" :
                                "BUILD_MUSCLE".equals(String.valueOf(p.getHealthGoal())) ? "增肌" : "维持",
                daily,
                p.getWeightKg() * 1.6, 1.6,
                daily * 0.45 / 4,
                daily * 0.30 / 9,
                diarySection,
                region,
                regionRecipe,
                getMockIngredient(0), getMockIngredient(1), getMockIngredient(2), getMockIngredient(3),
                getMockStep(0, regionRecipe), getMockStep(1, regionRecipe), getMockStep(2, regionRecipe),
                daily * 0.4, daily * 0.35 / 4, daily * 0.35 / 4, daily * 0.3 / 9,
                getMockRecipeB(),
                getMockIngredient(4), getMockIngredient(5), getMockIngredient(6), getMockIngredient(7),
                getMockStep(4, "第二道菜"), getMockStep(5, "第二道菜"), getMockStep(6, "第二道菜"),
                daily * 0.35, daily * 0.3 / 4, daily * 0.3 / 4, daily * 0.25 / 9
        );
    }

    private String getRegionRecipe(String region) {
        if (region.contains("华北") || region.contains("北京")) return "京酱鸡丝";
        if (region.contains("东北")) return "地三鲜（少油版）";
        if (region.contains("华东") || region.contains("上海")) return "清蒸鲈鱼";
        if (region.contains("华中")) return "粉蒸时蔬";
        if (region.contains("华南") || region.contains("广东")) return "白切鸡（去皮）";
        if (region.contains("西南") || region.contains("四川")) return "水煮鸡片";
        if (region.contains("西北")) return "孜然烤羊排（小份）";
        return "番茄炒蛋";
    }

    private String getMockRecipeB() {
        return "燕麦牛奶粥";
    }

    private String getMockIngredient(int idx) {
        String[][] ingredients = {
            {"鸡胸肉 200g", "西兰花 100g", "胡萝卜 50g", "香菇 3朵"},
            {"番茄 2个", "鸡蛋 3个", "葱花 少许", "橄榄油 1勺"},
            {"三文鱼 150g", "芦笋 100g", "柠檬 半个", "黑胡椒 适量"},
            {"豆腐 200g", "虾仁 100g", "小白菜 150g", "姜片 3片"},
            {"燕麦片 50g", "牛奶 300ml", "蓝莓 30g", "蜂蜜 1勺"},
            {"牛肉 150g", "青椒 2个", "洋葱 半个", "生抽 1勺"},
            {"紫薯 200g", "酸奶 100ml", "坚果碎 20g", "肉桂粉 少许"},
            {"鳕鱼 200g", "菠菜 150g", "蒜末 1勺", "白葡萄酒 少许"},
        };
        int row = idx % ingredients.length;
        int col = idx / ingredients.length;
        if (col >= 4) col = col % 4;
        return idx < 8 ? ingredients[row][col] : "时蔬 100g";
    }

    private String getMockStep(int idx, String dishName) {
        String[][] steps = {
            {"将主料洗净切好备用", "配菜焯水 2 分钟后捞出", "热锅少油翻炒调味即可出锅"},
            {"食材预处理 5 分钟", "中火慢烹至熟透", "出锅前撒上调料增香"},
            {"将所有食材按顺序备好", "依次下锅烹饪", "装盘后点缀即可享用"},
            {"主料腌制 10 分钟入味", "热锅凉油爆香配料", "加入主料翻炒至变色"},
            {"配料洗净切小块", "主料与配料混合烹饪", "调味后焖煮片刻"},
            {"预处理食材至合适大小", "按顺序翻炒至断生", "调味出锅保持原味"},
            {"食材准备阶段约 5 分钟", "烹饪阶段注意火候", "装盘并简单点缀"},
        };
        int row = idx % steps.length;
        return steps[row][idx % 3];
    }

    private String buildSummary(UserProfileRequest p, String foodDiaryContext) {
        double bmi = calcBmi(p.getHeightCm(), p.getWeightKg());
        String diaryNote = (foodDiaryContext != null && !foodDiaryContext.isEmpty())
                ? "已结合您近期的饮食记录进行分析。" : "";
        String goal = switch (p.getHealthGoal() == null ? "MAINTAIN" : p.getHealthGoal().name()) {
            case "LOSE_WEIGHT" -> "减脂";
            case "GAIN_WEIGHT" -> "增重";
            case "BUILD_MUSCLE" -> "增肌";
            case "IMPROVE_HEALTH" -> "改善健康";
            default -> "维持";
        };
        return String.format(
                "基于您的信息（%s岁，BMI %.1f，目标：%s），为您生成了个性化饮食建议。%s",
                p.getAge() != null ? p.getAge() : "—", bmi, goal, diaryNote
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
        String region = p.getRegion() != null && !p.getRegion().isBlank() ? p.getRegion() : "";
        String recipe1Name = getRegionRecipe(region);
        String regionTag = region.isEmpty() ? "" : " · " + region + "风味";

        RecommendationResponse.RecipeDto r1 = new RecommendationResponse.RecipeDto();
        r1.setName(recipe1Name + regionTag);
        r1.setDescription("结合当地特色食材，营养均衡");
        r1.setIngredients(List.of(getMockIngredient(0), getMockIngredient(1), getMockIngredient(2), getMockIngredient(3)));
        r1.setSteps(List.of(getMockStep(0, recipe1Name), getMockStep(1, recipe1Name), getMockStep(2, recipe1Name)));
        r1.setEstimatedTime("20 分钟");
        r1.setDifficulty("简单");
        r1.setTags(List.of("地方风味", "高蛋白", "营养均衡"));

        RecommendationResponse.NutritionalInfo n1 = new RecommendationResponse.NutritionalInfo();
        n1.setCalories(350.0); n1.setProtein(30.0); n1.setCarbs(15.0); n1.setFat(18.0);
        r1.setNutritionalInfo(n1);

        RecommendationResponse.RecipeDto r2 = new RecommendationResponse.RecipeDto();
        r2.setName("燕麦牛奶粥");
        r2.setDescription("早餐优选，提供持久能量和膳食纤维");
        r2.setIngredients(List.of("燕麦片 50g", "牛奶 300ml", "蓝莓 30g", "蜂蜜 1勺"));
        r2.setSteps(List.of(
                "燕麦片与牛奶同煮 3-5 分钟至浓稠",
                "倒入碗中，放上蓝莓",
                "淋上少量蜂蜜调味"
        ));
        r2.setEstimatedTime("10 分钟");
        r2.setDifficulty("简单");
        r2.setTags(List.of("早餐", "高纤维", "快手"));

        RecommendationResponse.NutritionalInfo n2 = new RecommendationResponse.NutritionalInfo();
        n2.setCalories(380.0); n2.setProtein(16.0); n2.setCarbs(52.0); n2.setFat(10.0);
        r2.setNutritionalInfo(n2);

        RecommendationResponse.RecipeDto r3 = new RecommendationResponse.RecipeDto();
        r3.setName("番茄豆腐虾仁汤");
        r3.setDescription("低卡高蛋白汤品，温暖又饱腹");
        r3.setIngredients(List.of("番茄 2个", "嫩豆腐 200g", "虾仁 100g", "葱花姜丝 少许"));
        r3.setSteps(List.of(
                "番茄去皮切块，热锅少油炒出汁",
                "加入清水煮沸，放入豆腐和虾仁",
                "煮 3 分钟加盐调味，撒葱花出锅"
        ));
        r3.setEstimatedTime("15 分钟");
        r3.setDifficulty("简单");
        r3.setTags(List.of("低卡", "高蛋白", "汤品"));

        RecommendationResponse.NutritionalInfo n3 = new RecommendationResponse.NutritionalInfo();
        n3.setCalories(220.0); n3.setProtein(25.0); n3.setCarbs(10.0); n3.setFat(6.0);
        r3.setNutritionalInfo(n3);

        return List.of(r1, r2, r3);
    }

    private List<String> generateTips(UserProfileRequest p, String foodDiaryContext) {
        List<String> tips = new java.util.ArrayList<>(List.of(
                "每天保持 8 杯水的摄入（约 2000ml）",
                "建议三餐定时定量，避免暴饮暴食",
                "加工食品和含糖饮料应尽量减少"
        ));

        if ("LOSE_WEIGHT".equals(String.valueOf(p.getHealthGoal()))) {
            tips.add("减脂期建议晚餐在 19:00 前完成，睡前 3 小时不进食");
            tips.add("每周安排 3-5 次有氧运动，每次 30-45 分钟");
        } else if ("BUILD_MUSCLE".equals(String.valueOf(p.getHealthGoal()))) {
            tips.add("增肌期注意训练后 30 分钟内补充蛋白质");
            tips.add("保证每日蛋白质摄入 1.6-2.0g/kg 体重");
        } else if ("GAIN_WEIGHT".equals(String.valueOf(p.getHealthGoal()))) {
            tips.add("可适当增加健康零食如坚果、酸奶等");
            tips.add("力量训练配合高蛋白饮食有助于健康增重");
        } else {
            tips.add("每周至少 150 分钟中等强度运动");
        }

        if (foodDiaryContext != null && !foodDiaryContext.isEmpty()) {
            tips.add("根据您的饮食记录，建议加强营养素的均衡摄入");
        }

        tips.add("保持充足睡眠（7-8 小时）有助于代谢和体重管理");
        return tips;
    }
}