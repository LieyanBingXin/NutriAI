package com.nutriai.service;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 意图路由表的行为契约测试。
 * matchIntent 是纯函数（不触达任何依赖），因此用全 null 依赖构造实例即可。
 * 任何对路由表顺序的调整，都必须让这里的断言依然成立或同步更新。
 */
class AssistantIntentServiceTest {

    private final AssistantIntentService service = new AssistantIntentService(
            null, null, null, null, null, null, null, null, null, null);

    @Test
    @DisplayName("空消息 → EMPTY")
    void emptyMessage() {
        assertEquals("EMPTY", service.matchIntent(""));
        assertEquals("EMPTY", service.matchIntent("   "));
        assertEquals("EMPTY", service.matchIntent(null));
    }

    @Test
    @DisplayName("一周食谱按钮不能被本周汇总吞掉")
    void weeklyPlanBeatsWeekSummary() {
        assertEquals("WEEKLY_PLAN", service.matchIntent("帮我规划一周食谱"));
        assertEquals("WEEKLY_PLAN", service.matchIntent("一周食谱"));
        assertEquals("WEEKLY_PLAN", service.matchIntent("给我安排一周三餐"));
        assertEquals("WEEKLY_PLAN", service.matchIntent("七天食谱计划"));
    }

    @Test
    @DisplayName("纯周汇总仍走 WEEK_SUMMARY")
    void weekSummaryStillWorks() {
        assertEquals("WEEK_SUMMARY", service.matchIntent("本周汇总"));
        assertEquals("WEEK_SUMMARY", service.matchIntent("最近7天吃得怎么样"));
        assertEquals("WEEK_SUMMARY", service.matchIntent("上周总结"));
    }

    @Test
    @DisplayName("今日查询优先级高于其他分支")
    void queryToday() {
        assertEquals("QUERY_TODAY", service.matchIntent("我今天吃了多少"));
        assertEquals("QUERY_TODAY", service.matchIntent("今日汇总"));
        assertEquals("QUERY_TODAY", service.matchIntent("查一下今天的记录"));
    }

    @Test
    @DisplayName("体重记录优先于体重查询")
    void weightRecordBeatsQuery() {
        assertEquals("WEIGHT_RECORD", service.matchIntent("今天体重 68 kg"));
        assertEquals("WEIGHT_RECORD", service.matchIntent("刚称了 65 公斤"));
        assertEquals("WEIGHT_QUERY", service.matchIntent("我的体重记录"));
        assertEquals("WEIGHT_QUERY", service.matchIntent("看看体重变化"));
    }

    @Test
    @DisplayName("「换一批，别管冰箱存货」不能触发冰箱入库")
    void reRecommendNeverPollutesFridge() {
        assertEquals("RE_RECOMMEND", service.matchIntent("换一批，别管我的冰箱存货了"));
        assertEquals("RE_RECOMMEND", service.matchIntent("换一批"));
        assertEquals("RE_RECOMMEND", service.matchIntent("重新推荐"));
    }

    @Test
    @DisplayName("明确的冰箱新增语义才进 FRIDGE_ADD")
    void fridgeAddOnlyForExplicitAdd() {
        assertEquals("FRIDGE_ADD", service.matchIntent("我家有鸡胸肉、西兰花"));
        assertEquals("FRIDGE_ADD", service.matchIntent("刚买了鸡蛋和牛奶"));
        assertEquals("FRIDGE_LIST", service.matchIntent("冰箱里有什么"));
        assertEquals("FRIDGE_CLEAR", service.matchIntent("清空冰箱"));
    }

    @Test
    @DisplayName("冰箱新增优先级高于偏好识别")
    void fridgeAddBeatsPreference() {
        assertEquals("FRIDGE_ADD", service.matchIntent("家里有我喜欢吃的鸡胸肉"));
    }

    @Test
    @DisplayName("饮食记录分支")
    void foodDiary() {
        assertEquals("FOOD_DIARY", service.matchIntent("早餐吃了鸡蛋加牛奶"));
        assertEquals("FOOD_DIARY", service.matchIntent("登记一下今天吃的"));
        assertEquals("FOOD_DIARY", service.matchIntent("200g 米饭 + 150g 鸡胸肉"));
    }

    @Test
    @DisplayName("偏好识别分支")
    void preference() {
        assertEquals("PREFERENCE", service.matchIntent("我喜欢吃辣，讨厌香菜"));
        assertEquals("PREFERENCE", service.matchIntent("我对海鲜过敏"));
        assertEquals("PREFERENCE", service.matchIntent("我不喜欢吃香菜"));
    }

    @Test
    @DisplayName("营养缺口 / 推荐 / 进度 / 画像 / 帮助 分支归属")
    void otherBranches() {
        assertEquals("GAP", service.matchIntent("我缺什么营养"));
        assertEquals("GAP", service.matchIntent("营养缺口分析"));
        assertEquals("RECOMMEND", service.matchIntent("今晚吃什么"));
        assertEquals("RECOMMEND", service.matchIntent("推荐一下减脂餐"));
        assertEquals("PROGRESS", service.matchIntent("今日反馈"));
        assertEquals("PROGRESS", service.matchIntent("我还差多少热量"));
        assertEquals("PROFILE_UPDATE", service.matchIntent("我要减肥"));
        assertEquals("PROFILE_UPDATE", service.matchIntent("我身高 175cm"));
        assertEquals("HELP", service.matchIntent("你能做什么"));
    }

    @Test
    @DisplayName("闲聊落到 AI 兜底")
    void fallbackToAi() {
        assertEquals("FALLBACK_AI", service.matchIntent("今天心情不太好"));
        assertEquals("FALLBACK_AI", service.matchIntent("谢谢你"));
    }

    @Test
    @DisplayName("餐别统一成中文")
    void mealTypeNormalization() {
        assertEquals("早餐", AssistantIntentService.normalizeMealType("breakfast"));
        assertEquals("午餐", AssistantIntentService.normalizeMealType("LUNCH"));
        assertEquals("晚餐", AssistantIntentService.normalizeMealType("dinner"));
        assertEquals("加餐", AssistantIntentService.normalizeMealType("snack"));
        assertEquals("早餐", AssistantIntentService.normalizeMealType("早餐"));
        assertEquals("午餐", AssistantIntentService.normalizeMealType(null));
    }

    @Test
    @DisplayName("冰箱/饮食/查询关键词互不抢占")
    void intentIsolation() {
        // “吃完了午饭”不应被当成从冰箱移除
        assertEquals("FOOD_DIARY", service.matchIntent("早餐吃完了"));
        assertEquals("FRIDGE_LIST", service.matchIntent("看看冰箱里有什么"));
        // 只要不是推荐类问题，“今天+记录”仍应走今日汇总
        assertEquals("QUERY_TODAY", service.matchIntent("查一下今天的记录"));
        // 推荐问题不能被冰箱新增吞掉
        assertEquals("RECOMMEND", service.matchIntent("我家有鸡胸肉，今晚吃什么"));
    }

    @Test
    @DisplayName("体重单位与最后一次命中")
    void weightParsingUnits() {
        assertEquals("WEIGHT_RECORD", service.matchIntent("今天称了 65 斤"));
        assertEquals("WEIGHT_RECORD", service.matchIntent("体重从 70kg 减到 68kg"));
        assertEquals("WEIGHT_QUERY", service.matchIntent("我现在多重"));
    }

    @Test
    @DisplayName("偏好支持并列与转折")
    void preferenceWithConjunctions() {
        assertEquals("PREFERENCE", service.matchIntent("我喜欢鸡蛋和牛奶"));
        assertEquals("PREFERENCE", service.matchIntent("喜欢鸡蛋但讨厌香菜"));
        assertEquals("PREFERENCE", service.matchIntent("我不吃香菜"));
    }

    // ========== 多轮上下文 ==========

    @Test
    @DisplayName("补充型消息单独不命中，拼上上一轮后命中饮食记录")
    void multiTurnSupplementHitsFoodDiary() {
        // 「一杯」没有数字，单看不是食物列表，也不会命中任何首轮规则
        assertEquals("FALLBACK_AI", service.matchIntent("再加一杯牛奶"));

        List<Map<String, String>> history = List.of(
                Map.of("role", "user", "content", "早餐吃了一个鸡蛋"),
                Map.of("role", "assistant", "content", "识别到：鸡蛋 · 早餐 · 72 kcal（今天），要记入饮食日记吗？"));
        assertEquals("FOOD_DIARY", service.matchIntent("再加一杯牛奶", history));
    }

    @Test
    @DisplayName("跳过 history 末尾与当前相同的消息（前端提前 push 的回归测试）")
    void multiTurnSkipsCurrentMessageInHistory() {
        List<Map<String, String>> history = List.of(
                Map.of("role", "user", "content", "早餐吃了一个鸡蛋"),
                Map.of("role", "assistant", "content", "识别到：鸡蛋 · 早餐 · 72 kcal（今天），要记入饮食日记吗？"),
                Map.of("role", "user", "content", "再加一杯牛奶")); // 前端在发送前就把当前消息也推进 history
        assertEquals("FOOD_DIARY", service.matchIntent("再加一杯牛奶", history));
    }

    @Test
    @DisplayName("augmentWithHistory：补充型拼上一轮用户消息")
    void augmentSupplements() {
        List<Map<String, String>> history = List.of(
                Map.of("role", "user", "content", "早餐吃了一个鸡蛋"));
        assertEquals("早餐吃了一个鸡蛋，再加一杯牛奶",
                service.augmentWithHistory("再加一杯牛奶", history));
    }

    @Test
    @DisplayName("augmentWithHistory：跳过末尾与当前相同的消息")
    void augmentSkipsCurrentMessage() {
        List<Map<String, String>> history = List.of(
                Map.of("role", "user", "content", "早餐吃了一个鸡蛋"),
                Map.of("role", "assistant", "content", "识别到鸡蛋"),
                Map.of("role", "user", "content", "再加一杯牛奶"));
        assertEquals("早餐吃了一个鸡蛋，再加一杯牛奶",
                service.augmentWithHistory("再加一杯牛奶", history));
    }

    @Test
    @DisplayName("augmentWithHistory：空历史、超长消息不增强")
    void augmentGuards() {
        assertEquals("再加一杯牛奶", service.augmentWithHistory("再加一杯牛奶", null));
        assertEquals("再加一杯牛奶", service.augmentWithHistory("再加一杯牛奶", List.of()));
        String longMsg = "再加一杯牛奶和一个水煮蛋还有一些西兰花以及两个全麦面包片";
        assertEquals(longMsg, service.augmentWithHistory(longMsg,
                List.of(Map.of("role", "user", "content", "早餐吃了一个鸡蛋"))));
    }

    @Test
    @DisplayName("augmentWithHistory：指代型取上一轮助手回复")
    void augmentRefersToAssistant() {
        List<Map<String, String>> history = List.of(
                Map.of("role", "user", "content", "推荐几道菜"),
                Map.of("role", "assistant", "content", "推荐了番茄炒蛋、西兰花炒鸡胸肉"));
        assertEquals("那个怎么做\n【上一轮助手回答了】：推荐了番茄炒蛋、西兰花炒鸡胸肉\n请结合上下文理解那个怎么做",
                service.augmentWithHistory("那个怎么做", history));
    }

    @Test
    @DisplayName("inheritMealTypeFromHistory：补充型继承上一轮餐别")
    void inheritMealType() {
        assertEquals("早餐", service.inheritMealTypeFromHistory("再加一杯牛奶",
                List.of(Map.of("role", "user", "content", "早餐吃了一个鸡蛋"))));
        assertEquals("午餐", service.inheritMealTypeFromHistory("再加一碗饭",
                List.of(Map.of("role", "user", "content", "午餐吃了米饭和鸡胸肉"))));
        assertEquals("晚餐", service.inheritMealTypeFromHistory("还要一个苹果",
                List.of(Map.of("role", "user", "content", "晚饭吃了一碗面"))));
        assertEquals("加餐", service.inheritMealTypeFromHistory("再记一个酸奶",
                List.of(Map.of("role", "user", "content", "加餐吃了一把坚果"))));
    }

    @Test
    @DisplayName("inheritMealTypeFromHistory：上一轮未提餐别、非补充型、空历史均不继承")
    void inheritMealTypeGuards() {
        assertEquals(null, service.inheritMealTypeFromHistory("再加一杯牛奶",
                List.of(Map.of("role", "user", "content", "吃了一个鸡蛋"))));
        assertEquals(null, service.inheritMealTypeFromHistory("今晚吃什么",
                List.of(Map.of("role", "user", "content", "早餐吃了一个鸡蛋"))));
        assertEquals(null, service.inheritMealTypeFromHistory("再加一杯牛奶", null));
        assertEquals(null, service.inheritMealTypeFromHistory("再加一杯牛奶", List.of()));
    }

    @Test
    @DisplayName("inheritMealTypeFromHistory：跳过末尾与当前相同的消息")
    void inheritMealTypeSkipsCurrent() {
        assertEquals("早餐", service.inheritMealTypeFromHistory("再加一杯牛奶",
                List.of(
                        Map.of("role", "user", "content", "早餐吃了一个鸡蛋"),
                        Map.of("role", "assistant", "content", "识别到鸡蛋"),
                        Map.of("role", "user", "content", "再加一杯牛奶"))));
    }

    @Test
    @DisplayName("inheritMealTypeFromHistory：修正型也继承上一轮餐别")
    void inheritMealTypeCorrection() {
        assertEquals("早餐", service.inheritMealTypeFromHistory("不是鸡蛋，是牛肉",
                List.of(Map.of("role", "user", "content", "早餐吃了个鸡蛋"))));
        assertEquals("晚餐", service.inheritMealTypeFromHistory("改成牛肉面",
                List.of(Map.of("role", "user", "content", "晚饭吃了米饭"))));
        assertEquals("午餐", service.inheritMealTypeFromHistory("应该是两个鸡蛋",
                List.of(Map.of("role", "user", "content", "午餐吃了一个鸡蛋"))));
    }

    @Test
    @DisplayName("inheritMealTypeFromHistory：上一轮用户没提餐别时退回助手回复里识别到的餐别")
    void inheritMealTypeFallsBackToAssistant() {
        assertEquals("午餐", service.inheritMealTypeFromHistory("再加一杯牛奶",
                List.of(
                        Map.of("role", "user", "content", "吃了一个鸡蛋"),
                        Map.of("role", "assistant", "content", "识别到：鸡蛋 · 午餐 · 72 kcal（今天），要记入饮食日记吗？"))));
    }

    @Test
    @DisplayName("inheritMealTypeFromHistory：纯指代型（不含「再加」）也继承餐别")
    void inheritMealTypeReferential() {
        assertEquals("晚餐", service.inheritMealTypeFromHistory("那个改成牛肉",
                List.of(Map.of("role", "user", "content", "晚饭吃了牛肉面"))));
    }

    @Test
    @DisplayName("修正型消息拼上上一轮后重新命中饮食记录")
    void multiTurnCorrectionHitsFoodDiary() {
        assertEquals("FALLBACK_AI", service.matchIntent("不是，是两个鸡蛋"));

        List<Map<String, String>> history = List.of(
                Map.of("role", "user", "content", "早餐吃了一个鸡蛋"),
                Map.of("role", "assistant", "content", "识别到：鸡蛋 · 早餐 · 72 kcal（今天），要记入饮食日记吗？"));
        assertEquals("FOOD_DIARY", service.matchIntent("不是，是两个鸡蛋", history));
    }

    @Test
    @DisplayName("augmentWithHistory：修正型拼上上一轮用户消息")
    void augmentCorrection() {
        List<Map<String, String>> history = List.of(
                Map.of("role", "user", "content", "早餐吃了一个鸡蛋"));
        assertEquals("早餐吃了一个鸡蛋，不是，是两个鸡蛋",
                service.augmentWithHistory("不是，是两个鸡蛋", history));
    }

    @Test
    @DisplayName("指代型拼上上一轮助手回复后命中推荐")
    void multiTurnRefersToAssistantHitsRecommend() {
        assertEquals("FALLBACK_AI", service.matchIntent("那个怎么做"));

        List<Map<String, String>> history = List.of(
                Map.of("role", "user", "content", "推荐几道菜"),
                Map.of("role", "assistant", "content", "推荐了番茄炒蛋、西兰花炒鸡胸肉"));
        assertEquals("RECOMMEND", service.matchIntent("那个怎么做", history));
    }

    // ========== 序数指代 & 时段餐别 ==========

    @Test
    @DisplayName("extractRecipeIndex：中文数字与阿拉伯数字序号")
    void extractRecipeIndexNumbers() {
        assertEquals(0, service.extractRecipeIndex("我吃了这第一个"));
        assertEquals(1, service.extractRecipeIndex("我吃了第二个"));
        assertEquals(2, service.extractRecipeIndex("我吃了第3个"));
        assertEquals(2, service.extractRecipeIndex("第三个"));
        assertEquals(9, service.extractRecipeIndex("第十个"));
        assertEquals(-2, service.extractRecipeIndex("我吃了最后一个"));
        assertEquals(-2, service.extractRecipeIndex("最后那道"));
    }

    @Test
    @DisplayName("extractRecipeIndex：无序号 / 空消息 / 非序数表达不命中")
    void extractRecipeIndexGuards() {
        assertEquals(-1, service.extractRecipeIndex("我吃了这个"));
        assertEquals(-1, service.extractRecipeIndex("再来一碗饭"));
        assertEquals(-1, service.extractRecipeIndex(null));
        assertEquals(-1, service.extractRecipeIndex(""));
        assertEquals(-1, service.extractRecipeIndex("给我来五个"));
    }

    @Test
    @DisplayName("mealTypeForHour：按时段映射餐别")
    void mealTypeForHourMapping() {
        assertEquals("早餐", service.mealTypeForHour(7));
        assertEquals("早餐", service.mealTypeForHour(9));
        assertEquals("午餐", service.mealTypeForHour(10));
        assertEquals("午餐", service.mealTypeForHour(13));
        assertEquals("加餐", service.mealTypeForHour(14));
        assertEquals("加餐", service.mealTypeForHour(16));
        assertEquals("晚餐", service.mealTypeForHour(17));
        assertEquals("晚餐", service.mealTypeForHour(21));
        assertEquals("加餐", service.mealTypeForHour(22));
        assertEquals("加餐", service.mealTypeForHour(2));
    }
}