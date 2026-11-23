package com.nutriai.config;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nutriai.dto.RecommendationResponse;
import com.nutriai.dto.UserProfileRequest;
import com.nutriai.entity.FavoriteRecipe;
import com.nutriai.entity.FoodDiary;
import com.nutriai.entity.FoodPreference;
import com.nutriai.entity.FridgeItem;
import com.nutriai.entity.RecipeBook;
import com.nutriai.entity.RecommendationRecord;
import com.nutriai.entity.User;
import com.nutriai.entity.UserAiPersona;
import com.nutriai.entity.UserProfile;
import com.nutriai.entity.WeightRecord;
import com.nutriai.repository.FavoriteRecipeRepository;
import com.nutriai.repository.FoodDiaryRepository;
import com.nutriai.repository.FoodPreferenceRepository;
import com.nutriai.repository.FridgeRepository;
import com.nutriai.repository.RecipeBookRepository;
import com.nutriai.repository.RecommendationRecordRepository;
import com.nutriai.repository.UserAiPersonaRepository;
import com.nutriai.repository.UserProfileRepository;
import com.nutriai.repository.UserRepository;
import com.nutriai.repository.WeightRecordRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

/**
 * 演示数据：首次启动且数据库中没有 demo2026 时，
 * 自动创建演示账号（含画像、近 3 天饮食、体重记录），方便直接体验。
 * 可通过 nutriai.demo-data.enabled=false 关闭。
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "nutriai.demo-data", name = "enabled", havingValue = "true", matchIfMissing = true)
public class DemoDataInitializer implements ApplicationRunner {

    public static final String DEMO_USERNAME = "demo2026";
    public static final String DEMO_PASSWORD = "NutriAI@123";

    /** 截图演示账号：普通中国用户画像，密码 123456，方便演示时快速登录 */
    public static final String SCREENSHOT_USERNAME = "zhangsan";
    public static final String SCREENSHOT_PASSWORD = "123456";

    private final UserRepository userRepository;
    private final UserProfileRepository profileRepository;
    private final FoodDiaryRepository foodDiaryRepository;
    private final WeightRecordRepository weightRecordRepository;
    private final FoodPreferenceRepository foodPreferenceRepository;
    private final FridgeRepository fridgeRepository;
    private final UserAiPersonaRepository userAiPersonaRepository;
    private final FavoriteRecipeRepository favoriteRecipeRepository;
    private final RecipeBookRepository recipeBookRepository;
    private final RecommendationRecordRepository recommendationRecordRepository;
    private final PasswordEncoder passwordEncoder;
    private final NutriAiProperties properties;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (!properties.getDemoData().isEnabled()) {
            return;
        }
        seedDemoIfAbsent();
        seedScreenshotAccountIfAbsent();
        seedZhangsanRichContent();
    }

    private void seedDemoIfAbsent() {
        if (userRepository.existsByUsername(DEMO_USERNAME)) {
            return;
        }
        try {
            seed();
            log.info("已创建演示账号: {} / {}", DEMO_USERNAME, DEMO_PASSWORD);
        } catch (Exception e) {
            log.warn("演示数据初始化失败（不影响正常使用）: {}", e.getMessage());
        }
    }

    private void seedScreenshotAccountIfAbsent() {
        if (userRepository.existsByUsername(SCREENSHOT_USERNAME)) {
            return;
        }
        try {
            seedZhangsan();
            log.info("已创建截图演示账号: {} / {}", SCREENSHOT_USERNAME, SCREENSHOT_PASSWORD);
        } catch (Exception e) {
            log.warn("截图演示账号初始化失败（不影响正常使用）: {}", e.getMessage());
        }
    }

    private void seed() {
        User user = new User(DEMO_USERNAME, "demo2026@nutriai.local",
                passwordEncoder.encode(DEMO_PASSWORD));
        user.setDisplayName("小绿");
        user.setEmailVerified(true);
        user = userRepository.save(user);

        UserProfile profile = new UserProfile();
        profile.setUser(user);
        profile.setGender("MALE");
        profile.setAge(28);
        profile.setHeightCm(175.0);
        profile.setWeightKg(70.0);
        profile.setActivityLevel("MODERATE");
        profile.setHealthGoal("LOSE_WEIGHT");
        profile.setTastePreferencesJson("[\"LIGHT\"]");
        profile.setRegion("华南（广东/广西/海南）");
        profile.setDailyCalorieGoal(2000.0);
        profile.setProteinGoal(80.0);
        profile.setCarbsGoal(250.0);
        profile.setFatGoal(65.0);
        profile.setFoodPreferencesJson("[\"鸡胸肉\",\"西兰花\",\"番茄\"]");
        profile.setFoodAllergiesJson("[\"香菜\"]");
        profileRepository.save(profile);

        LocalDate today = LocalDate.now();
        addDiary(user, today, "BREAKFAST", "燕麦鸡蛋粥", 350.0, 380.0, 18.0, 52.0, 10.0);
        addDiary(user, today, "LUNCH", "加大版鸡胸肉沙拉", 400.0, 620.0, 55.0, 35.0, 22.0);
        addDiary(user, today, "SNACK", "一杯牛奶", 250.0, 150.0, 8.0, 12.0, 8.0);
        addDiary(user, today, "DINNER", "清蒸鲈鱼 + 白灼菜心", 350.0, 420.0, 42.0, 20.0, 18.0);

        addDiary(user, today.minusDays(1), "BREAKFAST", "全麦三明治", 180.0, 320.0, 16.0, 38.0, 11.0);
        addDiary(user, today.minusDays(1), "LUNCH", "牛肉杂粮饭", 450.0, 680.0, 38.0, 78.0, 20.0);
        addDiary(user, today.minusDays(1), "DINNER", "番茄豆腐汤 + 米饭", 400.0, 460.0, 22.0, 58.0, 14.0);

        addDiary(user, today.minusDays(2), "BREAKFAST", "酸奶 + 香蕉", 250.0, 240.0, 9.0, 42.0, 5.0);
        addDiary(user, today.minusDays(2), "LUNCH", "虾仁蔬菜意面", 380.0, 560.0, 32.0, 70.0, 15.0);

        addWeight(user, today.minusDays(14), 72.6, "起点");
        addWeight(user, today.minusDays(10), 72.1, null);
        addWeight(user, today.minusDays(7), 71.5, null);
        addWeight(user, today.minusDays(3), 70.8, null);
        addWeight(user, today, 70.0, "今天");
    }

    /** 普通中国用户「张三」：32 岁上班族、轻度活动、减脂，内容贴近生活便于截图演示 */
    private void seedZhangsan() {
        User user = new User(SCREENSHOT_USERNAME, "zhangsan@nutriai.local",
                passwordEncoder.encode(SCREENSHOT_PASSWORD));
        user.setDisplayName("张三");
        user.setEmailVerified(true);
        user = userRepository.save(user);

        UserProfile profile = new UserProfile();
        profile.setUser(user);
        profile.setGender("MALE");
        profile.setAge(32);
        profile.setHeightCm(172.0);
        profile.setWeightKg(78.0);
        profile.setActivityLevel("LIGHT");
        profile.setHealthGoal("LOSE_WEIGHT");
        profile.setTastePreferencesJson("[\"SPICY\",\"SALTY\"]");
        profile.setRegion("华东（上海/江苏/浙江）");
        profile.setDailyCalorieGoal(2000.0);
        profile.setProteinGoal(95.0);
        profile.setCarbsGoal(220.0);
        profile.setFatGoal(60.0);
        profile.setFoodPreferencesJson("[\"米饭\",\"面条\",\"番茄炒蛋\",\"青椒肉丝\",\"红烧肉\",\"饺子\",\"苹果\"]");
        profile.setFoodAllergiesJson("[\"虾\",\"蟹\"]");
        profile.setHealthConditionsJson("[\"轻度脂肪肝\",\"久坐\"]");
        profileRepository.save(profile);

        // 喜欢吃
        addPreference(user, "米饭", "主食", "LIKE");
        addPreference(user, "面条", "主食", "LIKE");
        addPreference(user, "番茄炒蛋", "家常菜", "LIKE");
        addPreference(user, "青椒肉丝", "家常菜", "LIKE");
        addPreference(user, "红烧肉", "肉菜", "LIKE");
        addPreference(user, "饺子", "面点", "LIKE");
        addPreference(user, "苹果", "水果", "LIKE");

        // 不喜欢 / 忌口
        addPreference(user, "香菜", "蔬菜", "DISLIKE");
        addPreference(user, "苦瓜", "蔬菜", "DISLIKE");
        addPreference(user, "折耳根", "蔬菜", "DISLIKE");

        // 冰箱里有
        addFridge(user, "鸡蛋", "个", 10, LocalDate.now().plusDays(14));
        addFridge(user, "番茄", "个", 3, LocalDate.now().plusDays(5));
        addFridge(user, "青菜", "把", 1, LocalDate.now().plusDays(3));
        addFridge(user, "鸡胸肉", "g", 500, LocalDate.now().plusDays(4));
        addFridge(user, "土豆", "个", 3, LocalDate.now().plusDays(20));
        addFridge(user, "牛奶", "盒", 1, LocalDate.now().plusDays(10));
        addFridge(user, "青椒", "个", 3, LocalDate.now().plusDays(6));

        // AI 角色设定
        addPersona(user, "叫我小张");
        addPersona(user, "回复口语化一点");
        addPersona(user, "推荐快手家常菜");

        // 近 3 天饮食（典型中式三餐）
        LocalDate today = LocalDate.now();
        addDiary(user, today, "BREAKFAST", "豆浆 + 油条", 300.0, 380.0, 12.0, 48.0, 16.0);
        addDiary(user, today, "LUNCH", "米饭 + 青椒肉丝 + 番茄蛋汤", 520.0, 720.0, 30.0, 88.0, 24.0);
        addDiary(user, today, "SNACK", "一个苹果", 200.0, 104.0, 0.5, 28.0, 0.3);
        addDiary(user, today, "DINNER", "米饭 + 清炒时蔬 + 红烧鸡块", 480.0, 680.0, 40.0, 72.0, 22.0);

        addDiary(user, today.minusDays(1), "BREAKFAST", "小米粥 + 水煮蛋", 300.0, 260.0, 14.0, 40.0, 6.0);
        addDiary(user, today.minusDays(1), "LUNCH", "牛肉面", 550.0, 640.0, 34.0, 82.0, 18.0);
        addDiary(user, today.minusDays(1), "DINNER", "米饭 + 番茄炒蛋 + 蒜蓉青菜", 460.0, 560.0, 24.0, 66.0, 20.0);

        addDiary(user, today.minusDays(2), "BREAKFAST", "肉包子 + 豆浆", 280.0, 360.0, 14.0, 46.0, 12.0);
        addDiary(user, today.minusDays(2), "LUNCH", "米饭 + 红烧肉 + 炒青菜", 520.0, 780.0, 30.0, 70.0, 40.0);
        addDiary(user, today.minusDays(2), "DINNER", "韭菜猪肉饺子", 400.0, 560.0, 24.0, 70.0, 18.0);

        // 体重趋势：近一个月从 81kg 减到 78kg
        addWeight(user, today.minusDays(30), 81.0, "开始记录");
        addWeight(user, today.minusDays(22), 80.4, null);
        addWeight(user, today.minusDays(15), 79.8, null);
        addWeight(user, today.minusDays(8), 79.0, null);
        addWeight(user, today.minusDays(3), 78.3, null);
        addWeight(user, today, 78.0, "今天");
    }

    /** 为截图演示账号「张三」补齐收藏、食谱本、历史记录，便于完整演示各功能模块 */
    private void seedZhangsanRichContent() {
        User user = userRepository.findByUsername(SCREENSHOT_USERNAME).orElse(null);
        if (user == null) {
            return;
        }
        // 以“历史记录是否已存在”作为是否补齐过的判断标志
        if (recommendationRecordRepository.countByUserId(user.getId()) > 0) {
            return;
        }
        try {
            seedFavorites(user);
            seedRecipeBook(user);
            seedHistory(user);
            log.info("已为演示账号 {} 补齐收藏/食谱本/历史记录", SCREENSHOT_USERNAME);
        } catch (Exception e) {
            log.warn("演示账号内容补齐失败（不影响正常使用）: {}", e.getMessage());
        }
    }

    private void seedFavorites(User user) throws JsonProcessingException {
        addFavorite(user, buildRecipe("番茄炖牛腩", "酸甜开胃，牛腩软烂，减脂期补充优质蛋白的家常炖菜",
                List.of("牛腩 300g", "番茄 3 个", "洋葱 半个", "姜 3 片", "生抽 1 勺", "盐 少许", "香葱 1 根"),
                List.of("牛腩切块，冷水下锅焯水去血沫", "番茄顶部划十字，开水烫后去皮切块", "洋葱切丝，姜切片",
                        "热锅少油，下姜片洋葱炒香", "下番茄块炒出汤汁", "加入牛腩，加开水没过食材",
                        "小火炖 1.5 小时至软烂", "加生抽和盐调味，撒葱花即可"),
                420, 35, 16, 24, "90 分钟", "中等", List.of("高蛋白", "家常菜", "炖菜")));
        addFavorite(user, buildRecipe("青椒鸡胸肉丝", "低脂高蛋白的快手菜，青椒爽脆，适合带饭",
                List.of("鸡胸肉 200g", "青椒 2 个", "姜 2 片", "料酒 1 勺", "生抽 1 勺", "淀粉 半勺", "盐 少许"),
                List.of("鸡胸肉切丝，加料酒、淀粉抓匀腌制 10 分钟", "青椒去籽切丝，姜切丝",
                        "热锅少油，下姜丝爆香", "下鸡胸肉丝快速滑炒至变色", "下青椒丝大火翻炒 1 分钟", "加生抽和盐调味即可"),
                260, 32, 10, 9, "15 分钟", "简单", List.of("低脂", "高蛋白", "快手菜")));
        addFavorite(user, buildRecipe("香菇滑鸡", "鸡肉嫩滑，香菇鲜香，少油少盐的清淡蛋白菜",
                List.of("鸡腿肉 250g", "鲜香菇 6 朵", "姜 3 片", "料酒 1 勺", "生抽 1 勺", "蚝油 半勺", "小葱 1 根"),
                List.of("鸡腿肉去骨切块，加料酒、生抽腌制 15 分钟", "香菇洗净切片", "热锅少油，下姜片爆香",
                        "下鸡块翻炒至表面变色", "下香菇片翻炒 2 分钟", "加蚝油和少量清水焖 5 分钟", "撒葱花出锅"),
                320, 30, 8, 18, "25 分钟", "简单", List.of("高蛋白", "清淡", "家常菜")));
    }

    private void seedRecipeBook(User user) {
        addRecipeBook(user, "燕麦香蕉牛奶杯", "五分钟搞定的高纤早餐，饱腹感强", "breakfast",
                "即食燕麦 40g\n香蕉 1 根\n纯牛奶 250ml\n蓝莓 一小把",
                "燕麦倒入杯中\n加入牛奶浸泡 5 分钟\n香蕉切片铺在上面\n撒上蓝莓即可",
                380, 14, 60, 9, "早餐,高纤,快手");
        addRecipeBook(user, "青椒鸡胸肉丝", "低脂高蛋白快手菜，适合午餐带饭", "lunch",
                "鸡胸肉 200g\n青椒 2 个\n姜 2 片\n料酒 1 勺\n生抽 1 勺\n淀粉 半勺\n盐 少许",
                "鸡胸肉切丝，加料酒、淀粉抓匀腌制 10 分钟\n青椒去籽切丝\n热锅少油，下姜丝爆香\n下鸡胸肉丝滑炒至变色\n下青椒丝大火翻炒 1 分钟\n加生抽和盐调味即可",
                260, 32, 10, 9, "午餐,低脂,高蛋白");
        addRecipeBook(user, "番茄炖牛腩", "酸甜开胃的家常炖菜，晚餐补充蛋白", "dinner",
                "牛腩 300g\n番茄 3 个\n洋葱 半个\n姜 3 片\n生抽 1 勺\n盐 少许\n香葱 1 根",
                "牛腩切块焯水去血沫\n番茄去皮切块，洋葱切丝\n热锅少油炒香洋葱和姜\n下番茄炒出汁，加入牛腩\n加开水没过食材\n小火炖 1.5 小时\n调味撒葱花即可",
                420, 35, 16, 24, "晚餐,高蛋白,炖菜");
        addRecipeBook(user, "苹果酸奶杯", "下午加餐的清爽选择，低脂有饱腹感", "snack",
                "无糖酸奶 150g\n苹果 半个\n坚果碎 10g",
                "苹果切小丁\n与酸奶拌匀\n撒上坚果碎即可",
                180, 8, 22, 7, "加餐,低脂,水果");
    }

    private void seedHistory(User user) throws JsonProcessingException {
        UserProfileRequest profile = buildZhangsanProfile();

        RecommendationResponse.RecipeDto beef = buildRecipe("番茄炖牛腩", "酸甜开胃，牛腩软烂，减脂期补充优质蛋白的家常炖菜",
                List.of("牛腩 300g", "番茄 3 个", "洋葱 半个", "姜 3 片", "生抽 1 勺", "盐 少许", "香葱 1 根"),
                List.of("牛腩切块，冷水下锅焯水去血沫", "番茄顶部划十字，开水烫后去皮切块", "洋葱切丝，姜切片",
                        "热锅少油，下姜片洋葱炒香", "下番茄块炒出汤汁", "加入牛腩，加开水没过食材",
                        "小火炖 1.5 小时至软烂", "加生抽和盐调味，撒葱花即可"),
                420, 35, 16, 24, "90 分钟", "中等", List.of("高蛋白", "家常菜", "炖菜"));
        RecommendationResponse.RecipeDto chicken = buildRecipe("青椒鸡胸肉丝", "低脂高蛋白的快手菜，青椒爽脆，适合带饭",
                List.of("鸡胸肉 200g", "青椒 2 个", "姜 2 片", "料酒 1 勺", "生抽 1 勺", "淀粉 半勺", "盐 少许"),
                List.of("鸡胸肉切丝，加料酒、淀粉抓匀腌制 10 分钟", "青椒去籽切丝，姜切丝",
                        "热锅少油，下姜丝爆香", "下鸡胸肉丝快速滑炒至变色", "下青椒丝大火翻炒 1 分钟", "加生抽和盐调味即可"),
                260, 32, 10, 9, "15 分钟", "简单", List.of("低脂", "高蛋白", "快手菜"));
        RecommendationResponse.RecipeDto mushroom = buildRecipe("香菇滑鸡", "鸡肉嫩滑，香菇鲜香，少油少盐的清淡蛋白菜",
                List.of("鸡腿肉 250g", "鲜香菇 6 朵", "姜 3 片", "料酒 1 勺", "生抽 1 勺", "蚝油 半勺", "小葱 1 根"),
                List.of("鸡腿肉去骨切块，加料酒、生抽腌制 15 分钟", "香菇洗净切片", "热锅少油，下姜片爆香",
                        "下鸡块翻炒至表面变色", "下香菇片翻炒 2 分钟", "加蚝油和少量清水焖 5 分钟", "撒葱花出锅"),
                320, 30, 8, 18, "25 分钟", "简单", List.of("高蛋白", "清淡", "家常菜"));

        addHistory(user, profile,
                "根据你的身高 172cm、体重 78kg 和久坐的生活方式，建议每日摄入约 1800 kcal，配合高蛋白低脂饮食稳步减脂。",
                List.of(beef, chicken),
                List.of("三餐定时，避免宵夜", "每天步行 8000 步以上", "多喝水，少喝含糖饮料"));

        addHistory(user, profile,
                "针对上班族中午带饭的需求，推荐几道 30 分钟内出锅的快手家常菜，低油低盐。",
                List.of(mushroom, chicken),
                List.of("周末可以一次性备好一周的鸡胸肉", "带饭注意蛋白质与蔬菜搭配", "主食粗细搭配更扛饿"));
    }

    private UserProfileRequest buildZhangsanProfile() {
        UserProfileRequest profile = new UserProfileRequest();
        profile.setGender(UserProfileRequest.Gender.MALE);
        profile.setAge(32);
        profile.setHeightCm(172.0);
        profile.setWeightKg(78.0);
        profile.setActivityLevel(UserProfileRequest.ActivityLevel.LIGHT);
        profile.setHealthGoal(UserProfileRequest.HealthGoal.LOSE_WEIGHT);
        profile.setFoodPreferences(List.of("米饭", "面条", "番茄炒蛋", "青椒肉丝", "红烧肉", "饺子", "苹果"));
        profile.setFoodAllergies(List.of("虾", "蟹"));
        profile.setTastePreferences(List.of(UserProfileRequest.TastePreference.SPICY, UserProfileRequest.TastePreference.SALTY));
        profile.setRegion("华东（上海/江苏/浙江）");
        profile.setAdditionalNotes(List.of("久坐办公", "轻度脂肪肝"));
        return profile;
    }

    private RecommendationResponse.RecipeDto buildRecipe(String name, String description,
                                                         List<String> ingredients, List<String> steps,
                                                         int calories, int protein, int carbs, int fat,
                                                         String time, String difficulty, List<String> tags) {
        RecommendationResponse.RecipeDto recipe = new RecommendationResponse.RecipeDto();
        recipe.setName(name);
        recipe.setDescription(description);
        recipe.setIngredients(ingredients);
        recipe.setSteps(steps);
        RecommendationResponse.NutritionalInfo nutrition = new RecommendationResponse.NutritionalInfo();
        nutrition.setCalories(calories);
        nutrition.setProtein(protein);
        nutrition.setCarbs(carbs);
        nutrition.setFat(fat);
        recipe.setNutritionalInfo(nutrition);
        recipe.setEstimatedTime(time);
        recipe.setDifficulty(difficulty);
        recipe.setTags(tags);
        return recipe;
    }

    private void addFavorite(User user, RecommendationResponse.RecipeDto recipe) throws JsonProcessingException {
        if (favoriteRecipeRepository.existsByUserIdAndRecipeName(user.getId(), recipe.getName())) {
            return;
        }
        favoriteRecipeRepository.save(new FavoriteRecipe(user, recipe.getName(),
                objectMapper.writeValueAsString(recipe), recipe.getDescription()));
    }

    private void addRecipeBook(User user, String name, String description, String mealType,
                               String ingredientsText, String stepsText,
                               double calories, double protein, double carbs, double fat, String tags) {
        if (recipeBookRepository.existsByUserIdAndName(user.getId(), name)) {
            return;
        }
        RecipeBook recipe = new RecipeBook();
        recipe.setUser(user);
        recipe.setName(name);
        recipe.setDescription(description);
        recipe.setMealType(mealType);
        recipe.setIngredientsText(ingredientsText);
        recipe.setStepsText(stepsText);
        recipe.setCalories(calories);
        recipe.setProteinGrams(protein);
        recipe.setCarbsGrams(carbs);
        recipe.setFatGrams(fat);
        recipe.setTags(tags);
        recipeBookRepository.save(recipe);
    }

    private void addHistory(User user, UserProfileRequest profile,
                            String summary, List<RecommendationResponse.RecipeDto> recipes,
                            List<String> tips) throws JsonProcessingException {
        RecommendationResponse response = new RecommendationResponse();
        response.setSummary(summary);

        RecommendationResponse.NutritionalAdvice advice = new RecommendationResponse.NutritionalAdvice();
        advice.setDailyCalories(1800);
        advice.setProteinGrams(95);
        advice.setCarbsGrams(200);
        advice.setFatGrams(55);
        advice.setBmiCategory("超重（BMI 26.4）");
        advice.setMetabolismNote("基础代谢约 1650 kcal");
        response.setNutritionalAdvice(advice);
        response.setRecipes(recipes);
        response.setGeneralTips(tips);
        response.setAiProvider("mock");
        response.setGeneratedAt(System.currentTimeMillis());

        RecommendationRecord record = new RecommendationRecord();
        record.setUser(user);
        record.setProfileJson(objectMapper.writeValueAsString(profile));
        record.setResponseJson(objectMapper.writeValueAsString(response));
        record.setAiProvider("mock");
        recommendationRecordRepository.save(record);
    }

    private void addPreference(User user, String foodName, String category, String preference) {
        foodPreferenceRepository.save(new FoodPreference(user, foodName, category, preference));
    }

    private void addFridge(User user, String ingredient, String unit, Integer amountGram, LocalDate expiryDate) {
        FridgeItem item = new FridgeItem(user, ingredient);
        item.setUnit(unit);
        item.setAmountGram(amountGram);
        item.setExpiryDate(expiryDate);
        fridgeRepository.save(item);
    }

    private void addPersona(User user, String tagName) {
        UserAiPersona p = new UserAiPersona();
        p.setUserId(user.getId());
        p.setTagName(tagName);
        userAiPersonaRepository.save(p);
    }

    private void addDiary(User user, LocalDate date, String mealType, String name,
                          Double amount, Double calories, Double protein, Double carbs, Double fat) {
        FoodDiary d = new FoodDiary();
        d.setUser(user);
        d.setRecordDate(date);
        d.setMealType(mealType);
        d.setFoodName(name);
        d.setAmountGram(amount);
        d.setCalories(calories);
        d.setProtein(protein);
        d.setCarbs(carbs);
        d.setFat(fat);
        foodDiaryRepository.save(d);
    }

    private void addWeight(User user, LocalDate date, Double kg, String note) {
        WeightRecord r = new WeightRecord();
        r.setUser(user);
        r.setRecordDate(date);
        r.setWeightKg(kg);
        r.setNote(note);
        weightRecordRepository.save(r);
    }
}