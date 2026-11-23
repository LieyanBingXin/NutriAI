package com.nutriai.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nutriai.ai.AiServiceFactory;
import com.nutriai.dto.*;
import com.nutriai.service.FridgeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.*;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
public class AssistantIntentService {

    private final FoodDiaryService foodDiaryService;
    private final WeightTrackerService weightTrackerService;
    private final FoodPreferenceService foodPreferenceService;
    private final FridgeService fridgeService;
    private final AiCoachService aiCoachService;
    private final NutritionService nutritionService;
    private final AiServiceFactory aiServiceFactory;
    private final UserProfileService userProfileService;
    private final UserAiPersonaService userAiPersonaService;
    private final ObjectMapper objectMapper;

    private static final String ASSISTANT_SYSTEM_PROMPT =
            "你是 NutriAI 智能营养管家，是注册营养师级别的 AI 助手，运行在一个完整的网页应用里。\n" +
            "你的核心定位是：用专业的营养学知识 + 对用户数据的深度理解，给出具体可执行的饮食建议。\n" +
            "\n" +
            "【你的能力】你可以理解用户需求并提议操作，但所有写入操作需用户点击确认按钮后才会执行：\n" +
            "1. 识别体重记录 → 出示确认卡让用户确认\n" +
            "2. 识别饮食日记 → 出示确认卡（食物名 + 餐别 + 克重 + 热量）\n" +
            "3. 识别食物偏好（喜欢/讨厌） → 出示确认卡\n" +
            "4. 识别冰箱食材添加/移除 → 出示确认卡\n" +
            "5. 识别用户画像更新（年龄/身高/体重/目标） → 出示确认卡\n" +
            "6. 调用推荐引擎生成个性化食谱，结合今日缺口 + 冰箱食材 + 口味偏好\n" +
            "7. 查询今日/本周饮食汇总、分析营养缺口、给出每日进度反馈\n" +
            "8. 识别 AI 角色设定需求（如「你扮演温柔的女友」「用古风说话」） → 提示用户到看板添加\n" +
            "9. 撤销饮食记录 → 出示确认卡\n" +
            "\n" +
            "【AI 角色设定·重要】\n" +
            "- 系统上下文中可能包含「【AI 角色设定】」标签，这是用户在「我的看板」中设定的角色风格要求\n" +
            "- 如果存在角色设定标签，请将对应风格融入你的回复语气和表达方式中\n" +
            "- 例如标签是「温柔女友风格」，就用更亲密、温柔的语气回复；是「古风」，就用文白夹杂的古典语气\n" +
            "- 如果用户说「你扮演XX」「你变成XX」「从现在开始你是XX」等，这是想设置 AI 角色，回答末尾可以说「需要我把这个角色加到看板里吗？到看板 → AI 角色设定添加即可」\n" +
            "\n" +
            "【确认模式·重要】\n" +
            "- 当你想帮用户记录数据时，系统会自动生成确认按钮，你只需要自然地说「识别到XXX，要记入吗？」\n" +
            "- 不要假装已经执行（不要说「已记录」「已保存」），等用户点确认按钮后系统会处理\n" +
            "- 查询类操作（查进度、查缺口、推荐食谱）不需要确认，正常回复即可\n" +
            "\n" +
            "【多轮对话规则·最最重要·违反必错】\n" +
            "- 你会收到「最近对话历史」。务必逐条阅读，理解当前对话走到哪一步\n" +
            "- 用户说「那个」「刚才的」「上一次」「它」「换一个」——必须从历史中找到指代对象，不要装傻\n" +
            "- 如果你上一轮推荐了某道菜，这一轮用户说「换一个」「来点别的」，你必须推荐完全不同的菜，不能重复\n" +
            "- 历史中出现过的菜名（你推荐的），本轮请务必避开。推荐新菜\n" +
            "- 用户在对话中表达过的偏好（「我其实不太喜欢鱼」），即使没正式存入数据库，也要在后续推荐中体现\n" +
            "- 如果用户纠正过你（「我不是男的」「我不吃辣」），立即修正并在后续回合中记住\n" +
            "\n" +
            "【回复格式·硬性规定】\n" +
            "- 用 \\n 换行分段，每段 1-2 句，单段不超过 50 字\n" +
            "- 小标题以 emoji 开头：📊 今日进度 / 💡 建议 / 🍽️ 推荐 / ⚠️ 提醒 / 🎯 目标 / 🎭 角色\n" +
            "- 要点用「· 」开头，不用数字序号\n" +
            "- 每道推荐的食谱用 **菜名** — 简短说明 的格式，便于前端自动提取食谱卡片\n" +
            "- 关键数字（热量、克数、缺口）用加粗强调\n" +
            "- 总回复 3-6 段，不要长篇大论。多则不精\n" +
            "\n" +
            "【专业规则】\n" +
            "- 看到今日摄入时，先跟目标对比，再说差多少。如「蛋白还差 **25g**，推荐吃 **鸡胸肉 150g**」\n" +
            "- 推荐食谱时，要紧扣今日营养缺口 + 冰箱已有食材 + 用户口味偏好，三个维度都说清楚\n" +
            "- 不要给「多吃蔬菜」「注意均衡」这种废话。要具体到：什么食物、多少克、为什么\n" +
            "- 一次最多推荐 3 道菜，每道菜说明材料和为什么适合你今天的缺口\n" +
            "- 推荐格式严格：🍽️ 推荐\\n· **番茄牛腩** — 牛腩高蛋白补今日缺口，番茄维C助吸收\\n· **冬瓜排骨汤** — 冬瓜利尿去水肿，排骨钙质足\n" +
            "- 如果用户没说具体想吃什么，优先用冰箱里的食材来推荐\n" +
            "- 对每道推荐明确给出一句「为什么适合你」，用「—」而非「：」分隔以利前端解析\n" +
            "\n" +
            "【严禁】\n" +
            "- 严禁说「我只是个 AI 模型，仅供参考」或「请咨询专业营养师」\n" +
            "- 严禁说「已记录」「已保存」「已执行」——你没有直接写入权限，只有确认提议权\n" +
            "- 严禁编造你没有在数据里看到的数字\n" +
            "- 严禁对用户说教（「你应该」「你太胖了」这种话一句都不要有）\n" +
            "\n" +
            "【权威数据参考】\n" +
            "- 你的对话中会包含来自「中国居民膳食指南（2022）」和「中国食物成分表」的权威数据\n" +
            "- 回答营养问题时，优先引用这些官方数据支撑你的建议\n" +
            "- 引用时可以说「根据中国居民膳食指南」而不要说「根据数据库」\n" +
            "\n" +
            "语气：像懂营养学的好朋友，亲切、具体、不说废话。用「你」不用「您」。如果有 AI 角色设定标签，按标签调整语气。";

    /** 单条意图规则：id + 匹配器。优先级完全由列表顺序决定，先匹配先命中 */
    private record IntentRule(String id, Predicate<String> matcher) {}

    private final List<IntentRule> intentRules = buildIntentRules();

    private List<IntentRule> buildIntentRules() {
        return List.of(
                new IntentRule("QUERY_TODAY", this::isQueryTodayIntent),
                new IntentRule("WEEKLY_PLAN", this::isWeeklyPlanIntent),
                new IntentRule("WEEK_SUMMARY", this::isWeekSummaryIntent),
                new IntentRule("WEIGHT_RECORD", this::isWeightRecordIntent),
                new IntentRule("WEIGHT_QUERY", this::isWeightQueryIntent),
                new IntentRule("FRIDGE_ADD", this::isFridgeAddIntent),
                new IntentRule("FRIDGE_LIST", this::isFridgeListIntent),
                new IntentRule("FRIDGE_CLEAR", this::isFridgeClearIntent),
                new IntentRule("FRIDGE_REMOVE", this::isFridgeRemoveIntent),
                new IntentRule("UNDO_LAST_DIARY", this::isUndoLastDiaryIntent),
                new IntentRule("PREFERENCE", this::isPreferenceIntent),
                new IntentRule("FOOD_DIARY", this::isFoodDiaryIntent),
                new IntentRule("GAP", this::isGapIntent),
                new IntentRule("RE_RECOMMEND", this::isReRecommendIntent),
                new IntentRule("RECOMMEND", this::isRecommendIntent),
                new IntentRule("PROGRESS", this::isProgressIntent),
                new IntentRule("PROFILE_UPDATE", this::isProfileUpdateIntent),
                new IntentRule("HELP", this::isHelpIntent)
        );
    }

    /** 按优先级表解析意图 id；全部未命中返回 FALLBACK_AI（交给大模型兜底） */
    public String matchIntent(String rawMessage) {
        return matchIntent(rawMessage, null);
    }

    public String matchIntent(String rawMessage, java.util.List<java.util.Map<String, String>> history) {
        if (rawMessage == null || rawMessage.isBlank()) return "EMPTY";
        String m = rawMessage.trim();
        String firstRound = matchFirstRound(m);
        if (!firstRound.equals("FALLBACK_AI")) return firstRound;
        // 首轮没命中 → 尝试结合上一轮历史上下文
        String augmented = augmentWithHistory(m, history);
        if (!augmented.equals(m)) {
            for (IntentRule rule : intentRules) {
                if (rule.matcher().test(augmented)) return rule.id();
            }
        }
        return "FALLBACK_AI";
    }

    private String matchFirstRound(String msg) {
        for (IntentRule rule : intentRules) {
            if (rule.matcher().test(msg)) return rule.id();
        }
        return "FALLBACK_AI";
    }

    String augmentWithHistory(String current, java.util.List<java.util.Map<String, String>> history) {
        if (history == null || history.isEmpty() || current.length() > 20) return current;
        // 后续补充/修正型短消息 → 把上一轮用户消息拼进来
        if (matchesAny(current, "再加", "再记", "也记", "还有", "也加", "再来点", "还要", "另外", "顺便记",
                "也不", "也别", "也不要", "也不吃", "也讨厌", "也不喜欢",
                "不对是", "不是", "改成", "正确是", "应该是", "说错了是", "记错了是")) {
            String lastUser = findLastUserMessage(history, current);
            return (lastUser == null || lastUser.isBlank()) ? current : lastUser + "，" + current;
        }
        // 指代型：用户说「那个」「刚才的」→ 取上一轮助手消息推断
        if (matchesAny(current, "那个", "那一个", "刚才的", "刚刚的", "上一条", "这个", "它")) {
            String lastAssistant = findLastAssistantMessage(history);
            if (lastAssistant != null && !lastAssistant.isBlank()) {
                return current + "\n【上一轮助手回答了】：" + lastAssistant + "\n请结合上下文理解" + current;
            }
        }
        return current;
    }

    private boolean matchesAny(String msg, String... words) {
        for (String w : words) if (msg.contains(w)) return true;
        return false;
    }

    /** 延续型短消息（补充/修正/指代上一轮）才继承餐别，避免「今晚吃什么」这类新话题被误继承 */
    private boolean isContinuationMessage(String msg) {
        return matchesAny(msg,
                // 补充型
                "再加", "再记", "也记", "还有", "也加", "再来点", "还要", "另外", "顺便记",
                "也不", "也别", "也不要", "也不吃", "也讨厌", "也不喜欢",
                // 修正型
                "不对是", "不是", "改成", "正确是", "应该是", "说错了是", "记错了是",
                // 指代型
                "那个", "那一个", "刚才的", "刚刚的", "上一条", "这个", "它");
    }

    /** 取 history 中最后一条用户消息（跳过与当前相同的条目：前端会在发送前把当前消息也推进 history） */
    private String findLastUserMessage(java.util.List<java.util.Map<String, String>> history, String current) {
        for (int i = history.size() - 1; i >= 0; i--) {
            Map<String, String> turn = history.get(i);
            if ("user".equals(turn.getOrDefault("role", ""))) {
                String content = turn.getOrDefault("content", "");
                if (content != null && content.trim().equals(current.trim())) continue;
                return content;
            }
        }
        return null;
    }

    /** 取 history 中最后一条助手消息 */
    private String findLastAssistantMessage(java.util.List<java.util.Map<String, String>> history) {
        for (int i = history.size() - 1; i >= 0; i--) {
            Map<String, String> turn = history.get(i);
            if ("assistant".equals(turn.getOrDefault("role", ""))) {
                return turn.getOrDefault("content", "");
            }
        }
        return null;
    }

    /** 从文本里提取餐别；未提及返回 null 不覆盖 */
    private String extractMealType(String text) {
        if (text == null || text.isBlank()) return null;
        String s = text.trim();
        if (s.contains("早餐") || s.contains("早饭") || s.contains("早上")) return "早餐";
        if (s.contains("午餐") || s.contains("午饭") || s.contains("中餐") || s.contains("中午")) return "午餐";
        if (s.contains("晚餐") || s.contains("晚饭") || s.contains("晚上")) return "晚餐";
        if (s.contains("加餐") || s.contains("夜宵") || s.contains("宵夜")) return "加餐";
        return null;
    }

    /** 延续/修正/指代上一轮记录时继承餐别：优先从上一轮用户消息提取，退回上一轮助手回复（如「识别到：鸡蛋 · 早餐」） */
    String inheritMealTypeFromHistory(String current, java.util.List<java.util.Map<String, String>> history) {
        if (history == null || history.isEmpty()) return null;
        if (!isContinuationMessage(current)) return null;
        String meal = extractMealType(findLastUserMessage(history, current));
        if (meal != null) return meal;
        return extractMealType(findLastAssistantMessage(history));
    }

    /** 从「第N个 / 最后一个」提取推荐列表序号；0-based（0=第一道），-1 无，-2 最后一道 */
    int extractRecipeIndex(String msg) {
        if (msg == null) return -1;
        if (matchesAny(msg, "最后一个", "最后那个", "末尾那个", "最后一道", "最后这道", "最后那道")) return -2;
        Matcher m = Pattern.compile("第\\s*([一二三四五六七八九十两\\d]+)\\s*个").matcher(msg);
        if (m.find()) {
            int n = chineseNumberToInt(m.group(1));
            return n > 0 ? n - 1 : -1;
        }
        return -1;
    }

    /** 中文数字 / 阿拉伯数字 → int；无法识别返回 -1 */
    private int chineseNumberToInt(String s) {
        if (s == null || s.isBlank()) return -1;
        switch (s.trim()) {
            case "一": return 1;
            case "二": case "两": return 2;
            case "三": return 3;
            case "四": return 4;
            case "五": return 5;
            case "六": return 6;
            case "七": return 7;
            case "八": return 8;
            case "九": return 9;
            case "十": return 10;
        }
        try { return Integer.parseInt(s.trim()); } catch (NumberFormatException e) { return -1; }
    }

    /** 从挂载标签里提取食谱名（custom 标签形如「🍳 食谱「番茄鸡蛋盖浇饭」」）；没有则返回 null */
    private String extractRecipeNameFromTags(java.util.List<java.util.Map<String, String>> tags) {
        if (tags == null || tags.isEmpty()) return null;
        for (java.util.Map<String, String> t : tags) {
            String name = t.getOrDefault("name", "");
            if (name == null || name.isBlank()) continue;
            Matcher m = Pattern.compile("食谱[「“](.+?)[」”]").matcher(name);
            if (m.find()) {
                String recipe = m.group(1).trim();
                if (!recipe.isBlank()) return recipe;
            }
        }
        return null;
    }

    /** 按食谱名在已推荐列表里找热量，用于把挂载/拖拽的食谱直接计入日记 */
    private Double findCaloriesByRecipeName(java.util.List<java.util.Map<String, Object>> recipes, String name) {
        if (recipes == null || name == null || name.isBlank()) return null;
        for (java.util.Map<String, Object> r : recipes) {
            if (name.equals(r.get("name"))) {
                Object cal = r.get("calories");
                if (cal instanceof Number) return ((Number) cal).doubleValue();
            }
        }
        return null;
    }

    String mealTypeForHour(int hour) {
        if (hour >= 5 && hour < 10) return "早餐";
        if (hour >= 10 && hour < 14) return "午餐";
        if (hour >= 14 && hour < 17) return "加餐";
        if (hour >= 17 && hour < 22) return "晚餐";
        return "加餐";
    }

    /** 「主动建议」—— 打开助手页时按今日记录确定性生成一句建议，不依赖大模型，保证秒出稳定 */
    public Map<String, Object> proactiveTip(Long userId) {
        Map<String, Object> data = new LinkedHashMap<>();
        DailyNutritionDto today = null;
        try { today = foodDiaryService.getDailySummary(userId, LocalDate.now()); } catch (Exception ignored) {}

        int items = today == null ? 0 : today.getTotalItems();
        double cal = (today != null && today.getTotalCalories() != null) ? today.getTotalCalories() : 0;
        double goal = (today != null && today.getCalorieGoal() != null) ? today.getCalorieGoal() : 0;

        String tip;
        if (items == 0) {
            tip = "今天还没记饮食。说一句「早餐吃了个鸡蛋」，我就帮你记下并分析营养。";
        } else if (goal > 0) {
            long pct = Math.round(cal / goal * 100);
            if (pct < 50) {
                tip = "今日已摄入 " + Math.round(cal) + " kcal（约 " + pct + "%），进度偏低，别忘了按时用餐。";
            } else if (pct < 95) {
                tip = "今日已摄入 " + Math.round(cal) + " kcal（约 " + pct + "%），还剩约 " + Math.round(goal - cal) + " kcal 额度，晚餐注意均衡。";
            } else {
                tip = "今日已接近/超出热量目标（约 " + pct + "%），晚餐建议清淡收尾。";
            }
        } else {
            tip = "今日已记录 " + items + " 笔。设置热量目标后，我就能帮你盯住每天还能吃多少。";
        }

        data.put("tip", tip);
        data.put("items", items);
        data.put("calories", Math.round(cal));
        data.put("calPercent", goal > 0 ? Math.round(cal / goal * 100) : 0);
        return data;
    }

    public AssistantChatResponse chat(Long userId, String rawMessage, java.util.List<java.util.Map<String, String>> emphasizeTags, java.util.List<java.util.Map<String, String>> history, java.util.List<java.util.Map<String, Object>> suggestedRecipes) {
        AssistantChatResponse resp = new AssistantChatResponse();
        if (rawMessage == null || rawMessage.isBlank()) {
            resp.setReply("在呢 👋 我是你的 NutriAI 管家。今天想做点什么？记体重 / 记饮食 / 推荐菜 / 缺口分析 / 查看今日进度都行。");
            return resp;
        }
        String msg = rawMessage.trim();
        String intent = matchIntent(msg, history);

        // ========== 0. 预加载：一次查好今日日记 + 用户画像（避免各分支重复 N 次查询） ==========
        DailyNutritionDto today = null;
        try { today = foodDiaryService.getDailySummary(userId, LocalDate.now()); } catch (Exception ignored) {}
        UserProfileRequest profileCache = null;
        try { profileCache = fallbackProfile(userId); } catch (Exception ignored) {}
        if (profileCache == null) profileCache = new UserProfileRequest() {{
            setGender(UserProfileRequest.Gender.MALE);
            setAge(30); setHeightCm(170.0); setWeightKg(65.0);
            setActivityLevel(UserProfileRequest.ActivityLevel.MODERATE);
            setHealthGoal(UserProfileRequest.HealthGoal.MAINTAIN);
            setTastePreferences(java.util.List.of(UserProfileRequest.TastePreference.LIGHT));
        }};

        // ========== 1. 查类意图（先说"我有/没有"时先查真实数据） ==========
        if ("QUERY_TODAY".equals(intent)) {
            if (today != null && today.getTotalItems() > 0) {
                resp.setReply("📋 今日记录\n\n· 共 **" + today.getTotalItems() + "** 条 · 总 **"
                        + Math.round(today.getTotalCalories()) + " kcal**\n· 蛋白 "
                        + Math.round(today.getTotalProtein()) + "g · 碳水 "
                        + Math.round(today.getTotalCarbs()) + "g · 脂肪 "
                        + Math.round(today.getTotalFat()) + "g");
                resp.setData(today);
                try {
                    WeightStatsDto ws = weightTrackerService.getStats(userId);
                    if (ws != null && ws.getCurrentWeight() != null) {
                        resp.getTips().add("⚖️ 当前体重 **" + ws.getCurrentWeight() + " kg** · 变化 " + String.format("%.1f", ws.getChangeKg()) + " kg");
                    }
                } catch (Exception ignored) {}
                return resp;
            } else {
                resp.setReply("📋 今日记录\n\n还没记任何饮食哦～\n直接说「吃了 一个鸡蛋 + 牛奶」就行");
                resp.getTips().add("也可以告诉我「早餐吃了 XX」");
                return resp;
            }
        }

        // ========== 2. 一周食谱规划（必须先于本周汇总，避免「一周」被汇总分支吞掉） ==========
        if ("WEEKLY_PLAN".equals(intent)) {
            String planMsg = msg + "\n【任务】请按 7 天给出每日三餐的食谱规划表，每天 3 道菜，"
                    + "标注每道菜的热量估算与主要营养贡献，用「第N天」分段，控制在 7 段以内。";
            return fallbackChat(userId, planMsg, today, profileCache, emphasizeTags, history, suggestedRecipes);
        }

        // ========== 2.5 本周汇总 ==========
        if ("WEEK_SUMMARY".equals(intent)) {
            try {
                List<DailyNutritionDto> week = foodDiaryService.getWeeklySummary(userId, LocalDate.now());
                int days = week.size();
                double avgCal = week.stream().mapToDouble(d -> d.getTotalCalories() != null ? d.getTotalCalories() : 0).average().orElse(0);
                double avgPro = week.stream().mapToDouble(d -> d.getTotalProtein() != null ? d.getTotalProtein() : 0).average().orElse(0);
                resp.setReply("📅 近 7 天\n\n· 有记录 **" + days + "** 天\n· 日均 **" + Math.round(avgCal) + " kcal**\n· 日均蛋白 **" + Math.round(avgPro) + "g**");
                resp.setData(week);
                return resp;
            } catch (Exception e) {
                resp.setReply("这周数据暂时没拿到，再试试？");
                return resp;
            }
        }

        // ========== 3. 体重记录（待确认） ==========
        if ("WEIGHT_RECORD".equals(intent)) {
            Double weight = tryExtractWeight(msg);
            if (weight == null) {
                resp.setReply("没听清具体体重，直接说「今天 68 kg」这样就行。");
                return resp;
            }
            LocalDate recordDate = tryExtractRelativeDate(msg);
            WeightRequest wr = new WeightRequest();
            wr.setWeightKg(weight);
            wr.setRecordDate(recordDate);
            String dateLabel = recordDate.equals(LocalDate.now()) ? "今天" : recordDate.toString();
            resp.setReply("识别到体重：**" + weight + " kg**（" + dateLabel + "），要记入体重记录吗？");
            resp.getPendingActions().add(AssistantChatResponse.PendingAction.create(
                    "WEIGHT_RECORD",
                    dateLabel + " " + weight + " kg",
                    "/api/weight",
                    wr,
                    "weight"
            ));
            return resp;
        }

        // ========== 4. 查体重 ==========
        if ("WEIGHT_QUERY".equals(intent)) {
            try {
                WeightStatsDto ws = weightTrackerService.getStats(userId);
                if (ws == null || ws.getTotalRecords() == 0 || ws.getCurrentWeight() == null) {
                    resp.setReply("⚖️ 体重还没记过\n直接告诉我「今天 68 kg」我就记住啦");
                    return resp;
                }
                resp.setReply("⚖️ 体重记录\n\n· 当前 **" + ws.getCurrentWeight() + " kg**\n· 总变化 **"
                        + String.format("%.1f", ws.getChangeKg()) + " kg**\n· 共 **"
                        + ws.getTotalRecords() + "** 条记录");
                return resp;
            } catch (Exception e) {
                resp.setReply("⚖️ 体重还没记过\n直接告诉我「今天 68 kg」我就记住啦");
                return resp;
            }
        }

        // ========== 5. 冰箱记忆（待确认） ==========
        if ("FRIDGE_ADD".equals(intent)) {
            List<String> items = splitIngredients(msg);
            if (!items.isEmpty()) {
                resp.setReply("识别到冰箱食材：**" + String.join("、", items) + "**，要记入冰箱吗？");
                resp.getPendingActions().add(AssistantChatResponse.PendingAction.create(
                        "FRIDGE_ADD",
                        "记入冰箱：" + String.join("、", items),
                        "/api/assistant/fridge/remember",
                        Map.of("items", items),
                        "fridge"
                ));
                return resp;
            }
        }

        // ========== 5.1 纯查询冰箱里有什么 ==========
        if ("FRIDGE_LIST".equals(intent)) {
            List<String> list = fridgeService.list(userId);
            if (list.isEmpty()) {
                resp.setReply("🧊 冰箱记忆还是空的。告诉我「我家有鸡胸肉、西兰花」之类的我就记住啦。");
            } else {
                resp.setReply("🧊 你家冰箱里有 **" + list.size() + "** 样：" + String.join("、", list) + "。");
            }
            return resp;
        }

        // ========== 6. 清空冰箱（待确认） ==========
        if ("FRIDGE_CLEAR".equals(intent)) {
            resp.setReply("确认要**清空**冰箱里所有食材吗？");
            resp.getPendingActions().add(AssistantChatResponse.PendingAction.create(
                    "FRIDGE_CLEAR",
                    "清空冰箱",
                    "/api/assistant/fridge/forget",
                    Map.of("mode", "all"),
                    "fridge"
            ));
            return resp;
        }

        // ========== 7. 从冰箱移除（待确认） ==========
        if ("FRIDGE_REMOVE".equals(intent)) {
            String item = findFridgeItem(userId, msg);
            if (item != null) {
                resp.setReply("要从冰箱里移除「**" + item + "**」吗？");
                resp.getPendingActions().add(AssistantChatResponse.PendingAction.create(
                        "FRIDGE_REMOVE",
                        "移除：" + item,
                        "/api/assistant/fridge/forget",
                        Map.of("ingredient", item),
                        "fridge"
                ));
                return resp;
            }
        }

        // ========== 7.5 撤销上一条饮食记录（待确认） ==========
        if ("UNDO_LAST_DIARY".equals(intent)) {
            try {
                var opt = foodDiaryService.getLatestRecord(userId);
                if (opt.isEmpty()) {
                    resp.setReply("没有找到可以撤销的饮食记录～");
                    return resp;
                }
                FoodDiaryDto last = opt.get();
                resp.setReply("要撤销这条记录吗？**「" + last.getFoodName() + "」**· " + Math.round(last.getCalories()) + " kcal");
                resp.getPendingActions().add(AssistantChatResponse.PendingAction.create(
                        "UNDO_DIARY",
                        "撤销：" + last.getFoodName(),
                        "/api/food-diary/" + last.getId(),
                        null,
                        "food",
                        "DELETE"
                ));
                return resp;
            } catch (Exception e) {
                log.warn("撤销记录失败: {}", e.getMessage());
                resp.setReply("撤销失败了：" + e.getMessage());
                return resp;
            }
        }

        // ========== 8. 喜好 / 厌恶（待确认） ==========
        if ("PREFERENCE".equals(intent)) {
            List<String[]> parsed = parsePreferences(msg);
            if (!parsed.isEmpty()) {
                List<String> likes = new ArrayList<>();
                List<String> dislikes = new ArrayList<>();
                for (String[] p : parsed) {
                    String food = p[0];
                    boolean dislike = "DISLIKE".equals(p[1]);
                    if (dislike) dislikes.add(food); else likes.add(food);
                }
                StringBuilder sb = new StringBuilder("识别到偏好：");
                if (!likes.isEmpty()) sb.append("喜欢「").append(String.join("、", likes)).append("」");
                if (!likes.isEmpty() && !dislikes.isEmpty()) sb.append("，");
                if (!dislikes.isEmpty()) sb.append("不吃「").append(String.join("、", dislikes)).append("」");
                sb.append("。要记入偏好吗？");
                resp.setReply(sb.toString());
                // 每个食物单独一张确认卡：后端 /api/food-preference 按单个 foodName 存储
                for (String food : likes) {
                    resp.getPendingActions().add(AssistantChatResponse.PendingAction.create(
                            "PREFERENCE",
                            "喜欢：" + food,
                            "/api/food-preference",
                            Map.of("foodName", food, "category", "food", "preference", "LIKE"),
                            "food"
                    ));
                }
                for (String food : dislikes) {
                    resp.getPendingActions().add(AssistantChatResponse.PendingAction.create(
                            "PREFERENCE",
                            "避开：" + food,
                            "/api/food-preference",
                            Map.of("foodName", food, "category", "food", "preference", "DISLIKE"),
                            "food"
                    ));
                }
                return resp;
            }
        }

        // ========== 9. 记录饮食（待确认） ==========
        if ("FOOD_DIARY".equals(intent)) {
            try {
                FoodRecognitionResult recognized = null;
                // 序数指代上一轮推荐菜（如「我吃了这第一个」→ 取推荐列表第一道），避免被 AI 识别成「未知食物」
                int recipeIdx = extractRecipeIndex(msg);
                if (recipeIdx != -1 && suggestedRecipes != null && !suggestedRecipes.isEmpty()) {
                    int idx = recipeIdx == -2 ? suggestedRecipes.size() - 1 : recipeIdx;
                    if (idx >= 0 && idx < suggestedRecipes.size()) {
                        Map<String, Object> picked = suggestedRecipes.get(idx);
                        String name = picked != null ? (String) picked.get("name") : null;
                        Object cal = picked != null ? picked.get("calories") : null;
                        if (name != null && !name.isBlank() && cal instanceof Number) {
                            recognized = new FoodRecognitionResult(name, mealTypeForHour(java.time.LocalTime.now().getHour()), ((Number) cal).doubleValue(), null, null, null, null);
                        }
                    }
                }
                if (recognized == null) {
                    // 挂载了食谱标签、且用户用「这个/这道/那道」指代时，直接取挂载食谱，避免 AI 识别成「未指定食物」
                    String tagRecipe = extractRecipeNameFromTags(emphasizeTags);
                    if (tagRecipe != null && matchesAny(msg, "这个", "这道", "那道")) {
                        Double cal = findCaloriesByRecipeName(suggestedRecipes, tagRecipe);
                        String meal = extractMealType(msg);
                        recognized = new FoodRecognitionResult(
                                tagRecipe,
                                meal != null ? meal : mealTypeForHour(java.time.LocalTime.now().getHour()),
                                cal != null ? cal : 0.0,
                                null, null, null, null
                        );
                    }
                }
                if (recognized == null) {
                    recognized = aiServiceFactory.getService().recognizeFood(msg);
                }
                // 补充型消息（如「再加一杯牛奶」）餐别继承上一轮，避免 AI 把「加」误判成「加餐」；
                // 但若当前消息已明确说出餐别（如「记晚餐」），以明确餐别为准，不继承
                String inheritedMeal = inheritMealTypeFromHistory(msg, history);
                if (inheritedMeal != null && extractMealType(msg) == null) recognized.setMealType(inheritedMeal);
                FoodDiaryRequest request = new FoodDiaryRequest();
                request.setFoodName(recognized.getFoodName());
                request.setMealType(normalizeMealType(recognized.getMealType()));
                request.setCalories(recognized.getCalories());
                request.setProtein(recognized.getProteinGrams());
                request.setCarbs(recognized.getCarbsGrams());
                request.setFat(recognized.getFatGrams());
                request.setAmountGram(recognized.getServingSize());
                LocalDate diaryDate = tryExtractRelativeDate(msg);
                request.setRecordDate(diaryDate);
                String summary = recognized.getFoodName() + " · " + normalizeMealType(recognized.getMealType())
                        + " · " + Math.round(recognized.getCalories()) + " kcal";
                String dateTip = diaryDate.equals(LocalDate.now()) ? "今天" : diaryDate.toString();
                resp.setReply("识别到：**" + summary + "**（" + dateTip + "），要记入饮食日记吗？");
                resp.getPendingActions().add(AssistantChatResponse.PendingAction.create(
                        "FOOD_DIARY",
                        summary,
                        "/api/food-diary",
                        request,
                        "food"
                ));
                return resp;
            } catch (Exception e) {
                log.warn("饮食识别失败: {}", e.getMessage());
                resp.setReply("抱歉，这次没识别出来。试试直接说「早餐吃了一个鸡蛋加 100g 燕麦」？");
                return resp;
            }
        }

        // ========== 10. 营养缺口 ==========
        if ("GAP".equals(intent)) {
            try {
                NutritionGapDto gap = aiCoachService.getNutritionGap(userId, profileCache);
                resp.setData(gap);
                String insight = gap.getAiInsight() != null ? gap.getAiInsight() : "近 7 日有 " + gap.getTrackedDays() + " 天记录";
                resp.setReply("📊 营养缺口\n\n" + insight);
                return resp;
            } catch (Exception e) {
                resp.setReply("缺口分析暂时没跑出来，稍后再试～");
                return resp;
            }
        }

        // ========== 10.5 换一批 / 重新推荐（忽略冰箱，强制走通用推荐引擎） ==========
        if ("RE_RECOMMEND".equals(intent)) {
            String tagNotes = buildEmphasizeNotes(emphasizeTags);
            try {
                RecommendationResponse rr = nutritionService.getRecommendations(profileCache, userId, true, tagNotes);
                resp.setData(rr);
                String prefix = containsAny(msg, "别管", "不用管", "不管", "忽略", "别用", "不要用")
                        ? "🥗 好的，这次不参考冰箱存货，重新给你推荐 👇\n\n"
                        : "🥗 换一批来了 👇\n\n";
                resp.setReply(prefix + (rr.getSummary() != null ? rr.getSummary() : ""));
                resp.getTips().add("想要更多可以说「换一批」，或者告诉我口味偏好");
                return resp;
            } catch (Exception e) {
                resp.setReply("推荐引擎暂时离线，稍后再试试～");
                return resp;
            }
        }

        // ========== 11. 推荐 ==========
        if ("RECOMMEND".equals(intent)) {
            String tagNotes = buildEmphasizeNotes(emphasizeTags);
            boolean ignoreFridge = containsAny(msg, "别管", "不用管", "不管", "忽略", "别用", "不要用",
                    "别考虑", "不用考虑", "别参考", "不要参考");
            List<String> fridge = ignoreFridge ? List.of() : fridgeService.list(userId);
            RecommendationResponse rr;
            try {
                if (!fridge.isEmpty()) {
                    FridgeRequest fr = new FridgeRequest();
                    fr.setProfile(profileCache);
                    fr.setIngredients(fridge);
                    fr.setNotes(tagNotes);
                    rr = aiCoachService.getFridgeRecommendation(userId, fr);
                } else {
                    rr = nutritionService.getRecommendations(profileCache, userId, true, tagNotes);
                }
                resp.setData(rr);
                resp.setReply(rr.getSummary() != null ? "🥗 今日推荐\n\n" + rr.getSummary() : "🥗 已生成个性化推荐！");
                return resp;
            } catch (Exception e) {
                resp.setReply("推荐引擎暂时离线，稍后再试试～");
                return resp;
            }
        }

        // ========== 12. 今日进度反馈 ==========
        if ("PROGRESS".equals(intent)) {
            try {
                CoachFeedbackRequest cfr = new CoachFeedbackRequest();
                cfr.setProfile(profileCache);
                CoachFeedbackResponse f = aiCoachService.getCoachFeedback(userId, cfr);
                resp.setData(f);
                resp.setReply(f.getMessage() != null ? f.getMessage() : "今日反馈已更新 👇");
                if (f.getRemainingSummary() != null) resp.getActions().add(f.getRemainingSummary());
                return resp;
            } catch (Exception e) {
                resp.setReply("反馈计算暂时没跑出来，稍后再试。");
                return resp;
            }
        }

        // ========== 13. 更新画像（待确认） ==========
        if ("PROFILE_UPDATE".equals(intent)) {
            try {
                UserProfileDto existing = null;
                try { existing = userProfileService.getProfile(userId); } catch (Exception ignored) {}
                UserProfileDto dto = existing != null ? existing : new UserProfileDto();
                boolean changed = false;
                List<String> changes = new ArrayList<>();
                Matcher ageM = Pattern.compile("(\\d{2,3})岁").matcher(msg);
                if (ageM.find()) { dto.setAge(Integer.parseInt(ageM.group(1))); changed = true; changes.add("年龄：" + ageM.group(1) + "岁"); }
                Matcher hM = Pattern.compile("(\\d{3})\\s*(?:cm|厘米)").matcher(msg);
                if (hM.find()) { dto.setHeightCm(Double.parseDouble(hM.group(1))); changed = true; changes.add("身高：" + hM.group(1) + "cm"); }
                Matcher wM = Pattern.compile("([1-9]\\d{0,2}(?:\\.\\d{1,2})?)\\s*(?:kg|公斤)").matcher(msg);
                if (wM.find()) { dto.setWeightKg(Double.parseDouble(wM.group(1))); changed = true; changes.add("体重：" + wM.group(1) + "kg"); }
                if (containsAny(msg, "减脂", "减肥", "瘦")) { dto.setHealthGoal("LOSE_WEIGHT"); changed = true; changes.add("目标：减脂"); }
                else if (containsAny(msg, "增肌", "肌肉", "长肉")) { dto.setHealthGoal("BUILD_MUSCLE"); changed = true; changes.add("目标：增肌"); }
                else if (containsAny(msg, "增重", "变重")) { dto.setHealthGoal("GAIN_WEIGHT"); changed = true; changes.add("目标：增重"); }
                else if (containsAny(msg, "维持", "保持", "稳定")) { dto.setHealthGoal("MAINTAIN"); changed = true; changes.add("目标：维持"); }
                if (changed) {
                    resp.setReply("识别到画像更新：**" + String.join("、", changes) + "**，要保存吗？");
                    resp.getPendingActions().add(AssistantChatResponse.PendingAction.create(
                            "PROFILE_UPDATE",
                            String.join("、", changes),
                            "/api/user/profile",
                            dto,
                            "profile",
                            "PUT"
                    ));
                    return resp;
                }
            } catch (Exception e) {
                log.warn("画像更新失败: {}", e.getMessage());
            }
        }

        // ========== 14. 求助 / 你能做什么 ==========
        if ("HELP".equals(intent)) {
            resp.setReply("我是你的 NutriAI 全能管家，可以帮你做这些 👇");
            resp.getActions().add("⚖️ 记体重：「今天 68 kg」 → 确认后记录");
            resp.getActions().add("🍽️ 记饮食：「早餐吃了鸡蛋 + 牛奶」 → 确认后记录");
            resp.getActions().add("🧊 记冰箱：「我家有鸡胸肉、西兰花」 → 确认后记录");
            resp.getActions().add("💚 记喜好：「我喜欢吃辣 / 讨厌香菜」 → 确认后记录");
            resp.getActions().add("🎭 AI设定：「你扮演温柔的女友」 → 存入看板");
            resp.getActions().add("🥗 要推荐：「今晚吃什么」");
            resp.getActions().add("📊 查缺口：「我缺什么」");
            resp.getActions().add("📋 查进度：「今天吃了多少」");
            resp.getTips().add("所有写入操作都会先问你确认，不会偷偷记东西 👀");
            return resp;
        }

        // ========== 15. AI 兜底（带完整真实上下文 + 历史对话 + 身份设定） ==========
        return fallbackChat(userId, msg, today, profileCache, emphasizeTags, history, suggestedRecipes);
    }

    private AssistantChatResponse fallbackChat(Long userId, String msg, DailyNutritionDto today, UserProfileRequest profileCache, java.util.List<java.util.Map<String, String>> emphasizeTags, java.util.List<java.util.Map<String, String>> history, java.util.List<java.util.Map<String, Object>> suggestedRecipes) {
        AssistantChatResponse resp = new AssistantChatResponse();
        resp.setFromAi(true);
        try {
            String ctx = buildChatContext(userId, msg, today, profileCache, emphasizeTags, history, suggestedRecipes);
            String reply = aiServiceFactory.getService().chatWithContext(ASSISTANT_SYSTEM_PROMPT, ctx);
            resp.setReply(reply);
        } catch (Exception e) {
            log.warn("AI 兜底失败: {}", e.getMessage());
            resp.setReply("抱歉 AI 暂时离线，但简单指令我也能帮你：记体重 / 记饮食 / 冰箱有 X / 推荐 / 缺口");
        }
        return resp;
    }

    /**
     * 构建 AI 兜底对话的完整上下文字符串（供 chat 和 chatStream 共用）
     */
    private String buildChatContext(Long userId, String msg, DailyNutritionDto today, UserProfileRequest profileCache, java.util.List<java.util.Map<String, String>> emphasizeTags, java.util.List<java.util.Map<String, String>> history, java.util.List<java.util.Map<String, Object>> suggestedRecipes) {
        UserProfileRequest p = profileCache;
        StringBuilder ctx = new StringBuilder();
        ctx.append("【用户画像】").append(p.getGender()).append("，").append(p.getAge()).append("岁，")
                .append(p.getHeightCm()).append("cm / ").append(p.getWeightKg())
                .append("kg，目标：").append(p.getHealthGoal()).append("，活动：").append(p.getActivityLevel())
                .append("，口味：").append(joinTastes(p.getTastePreferences())).append("\n");
        try {
            String prefs = foodPreferenceService.buildPreferenceContext(userId);
            if (prefs != null) ctx.append("【偏好】").append(prefs).append("\n");
        } catch (Exception ignored) {}
        try {
            String fridge = fridgeService.buildFridgeContext(userId);
            if (fridge != null) ctx.append("【冰箱】").append(fridge).append("\n");
        } catch (Exception ignored) {}
        try {
            String persona = userAiPersonaService.buildContext(userId);
            if (persona != null) ctx.append(persona);
        } catch (Exception ignored) {}
        if (today != null && today.getTotalItems() > 0) {
            ctx.append("【今日真实记录】有 ").append(today.getTotalItems()).append(" 条，共 ")
                    .append(Math.round(today.getTotalCalories())).append(" kcal，蛋白 ")
                    .append(Math.round(today.getTotalProtein())).append("g，碳水 ")
                    .append(Math.round(today.getTotalCarbs())).append("g，脂肪 ")
                    .append(Math.round(today.getTotalFat())).append("g\n");
        } else {
            ctx.append("【今日真实记录】暂无\n");
        }
        String tagNotes = buildEmphasizeNotes(emphasizeTags);
        if (tagNotes != null && !tagNotes.isBlank()) {
            ctx.append("【本轮会话强调标签·请务必重视】").append(tagNotes).append("\n");
        }
        if (history != null && !history.isEmpty()) {
            int start = Math.max(0, history.size() - 10);
            ctx.append("【最近对话历史·用于理解上下文】\n");
            for (int i = start; i < history.size(); i++) {
                Map<String, String> turn = history.get(i);
                String role = turn.getOrDefault("role", "");
                String content = turn.getOrDefault("content", "");
                if (!content.isBlank()) {
                    ctx.append(role.equals("user") ? "用户" : "助手").append("：").append(content).append("\n");
                }
            }
        }
        ctx.append("当前用户说：").append(msg);

        if (suggestedRecipes != null && !suggestedRecipes.isEmpty()) {
            StringBuilder recCtx = new StringBuilder("【本轮已推荐食谱·请勿重复推荐】");
            for (Map<String, Object> sr : suggestedRecipes) {
                Object name = sr.get("name");
                if (name != null) recCtx.append(name.toString()).append("、");
            }
            recCtx.append("\n");
            ctx.insert(0, recCtx.toString());
        }
        return ctx.toString();
    }

    /**
     * 流式对话 —— 意图匹配结果即时返回，AI 兜底走 SSE token-by-token 流
     */
    public reactor.core.publisher.Flux<String> chatStream(Long userId, String rawMessage, java.util.List<java.util.Map<String, String>> emphasizeTags, java.util.List<java.util.Map<String, String>> history, java.util.List<java.util.Map<String, Object>> suggestedRecipes) {
        // 先用同步意图匹配判断（不触发 AI，避免兜底场景重复调用两次大模型）
        String intent = matchIntent(rawMessage, history);

        // 意图匹配命中 → 一次性打包返回
        if (!"FALLBACK_AI".equals(intent)) {
            AssistantChatResponse resp = chat(userId, rawMessage, emphasizeTags, history, suggestedRecipes);
            try {
                String json = objectMapper.writeValueAsString(resp);
                return reactor.core.publisher.Flux.just(json, "[DONE]");
            } catch (Exception e) {
                return reactor.core.publisher.Flux.just("{\"reply\":\"处理出错\"}", "[DONE]");
            }
        }

        // AI 兜底 → 直接走流式（只调用一次大模型）
        DailyNutritionDto today = null;
        try { today = foodDiaryService.getDailySummary(userId, java.time.LocalDate.now()); } catch (Exception ignored) {}
        UserProfileRequest profileCache = fallbackProfile(userId);
        String ctx = buildChatContext(userId, rawMessage, today, profileCache, emphasizeTags, history, suggestedRecipes);

        return aiServiceFactory.getService().chatStreamWithContext(ASSISTANT_SYSTEM_PROMPT, ctx)
                .map(token -> {
                    try {
                        return "{\"token\":" + objectMapper.writeValueAsString(token) + "}";
                    } catch (Exception e) {
                        return "{\"token\":\"?\"}";
                    }
                })
                .concatWith(reactor.core.publisher.Flux.just("[DONE]"))
                .onErrorResume(e -> {
                    log.warn("流式对话失败: {}", e.getMessage());
                    return reactor.core.publisher.Flux.just(
                            "{\"reply\":\"AI 暂时离线，但简单指令我也能帮你：记体重 / 记饮食 / 冰箱有 X / 推荐 / 缺口\"}",
                            "[DONE]"
                    );
                });
    }

    private UserProfileRequest fallbackProfile(Long userId) {
        UserProfileRequest p = new UserProfileRequest();
        p.setGender(UserProfileRequest.Gender.MALE);
        p.setAge(30); p.setHeightCm(170.0); p.setWeightKg(65.0);
        p.setActivityLevel(UserProfileRequest.ActivityLevel.MODERATE);
        p.setHealthGoal(UserProfileRequest.HealthGoal.MAINTAIN);
        p.setTastePreferences(java.util.List.of(UserProfileRequest.TastePreference.LIGHT));
        try {
            UserProfileDto existing = userProfileService.getProfile(userId);
            if (existing != null) {
                if (existing.getAge() != null) p.setAge(existing.getAge());
                if (existing.getHeightCm() != null) p.setHeightCm(existing.getHeightCm());
                if (existing.getWeightKg() != null) p.setWeightKg(existing.getWeightKg());
                if (existing.getGender() != null) p.setGender(safeGender(existing.getGender()));
                if (existing.getActivityLevel() != null) p.setActivityLevel(safeActivity(existing.getActivityLevel()));
                if (existing.getHealthGoal() != null) p.setHealthGoal(safeGoal(existing.getHealthGoal()));
                if (existing.getTastePreferences() != null) p.setTastePreferences(safeTastes(existing.getTastePreferences()));
                if (existing.getRegion() != null) p.setRegion(existing.getRegion());
            }
        } catch (Exception ignored) {}
        return p;
    }

    private UserProfileRequest.Gender safeGender(String s) { try { return UserProfileRequest.Gender.valueOf(s.toUpperCase()); } catch (Exception e) { return UserProfileRequest.Gender.MALE; } }
    private UserProfileRequest.ActivityLevel safeActivity(String s) { try { return UserProfileRequest.ActivityLevel.valueOf(s.toUpperCase()); } catch (Exception e) { return UserProfileRequest.ActivityLevel.MODERATE; } }
    private UserProfileRequest.HealthGoal safeGoal(String s) { try { return UserProfileRequest.HealthGoal.valueOf(s.toUpperCase()); } catch (Exception e) { return UserProfileRequest.HealthGoal.MAINTAIN; } }
    private java.util.List<UserProfileRequest.TastePreference> safeTastes(java.util.List<String> list) {
        if (list == null || list.isEmpty()) return java.util.List.of(UserProfileRequest.TastePreference.LIGHT);
        java.util.List<UserProfileRequest.TastePreference> out = new java.util.ArrayList<>();
        for (String s : list) {
            try { out.add(UserProfileRequest.TastePreference.valueOf(s.toUpperCase())); } catch (Exception ignored) {}
        }
        return out.isEmpty() ? java.util.List.of(UserProfileRequest.TastePreference.LIGHT) : out;
    }
    private String joinTastes(java.util.List<UserProfileRequest.TastePreference> list) {
        if (list == null || list.isEmpty()) return "未知";
        return String.join("、", list.stream().map(Enum::name).toList());
    }

    private Double tryExtractWeight(String msg) {
        Double val = null;
        // 明确的重量单位优先；取最后一次命中（"从 70kg 减到 68kg" 记为 68）
        Pattern p = Pattern.compile("([1-9]\\d{0,2}(?:\\.\\d{1,2})?)\\s*(kg|KG|公斤|千克|斤|两)");
        Matcher m = p.matcher(msg);
        while (m.find()) {
            double raw = Double.parseDouble(m.group(1));
            String unit = m.group(2);
            double kg = switch (unit) {
                case "斤" -> raw / 2.0;
                case "两" -> raw / 20.0;
                default -> raw;
            };
            String before = msg.substring(Math.max(0, m.start() - 6), m.start());
            if (before.matches(".*[¥元块].*") || before.contains("花了")) continue;
            if (kg >= 20 && kg <= 300) val = kg;
        }

        // 没有单位时，用体重关键词兜底
        if (val == null) {
            Pattern p3 = Pattern.compile("(?:体重|称了|称的|刚称|现在是|现在|应该是|大概是|就是|纠正|修正|更正)[^\\d]{0,6}([1-9]\\d{0,2}(?:\\.\\d{1,2})?)");
            Matcher m3 = p3.matcher(msg);
            while (m3.find()) {
                double v = Double.parseDouble(m3.group(1));
                if (v >= 20 && v <= 300) val = v;
            }
        }
        return val;
    }

    private LocalDate tryExtractRelativeDate(String msg) {
        LocalDate today = LocalDate.now();
        if (containsAny(msg, "大前天")) return today.minusDays(3);
        if (containsAny(msg, "前天")) return today.minusDays(2);
        if (containsAny(msg, "昨天", "昨日")) return today.minusDays(1);
        if (containsAny(msg, "今天", "今日")) return today;
        Pattern daysAgo = Pattern.compile("(\\d+)\\s*天前");
        Matcher dm = daysAgo.matcher(msg);
        if (dm.find()) return today.minusDays(Long.parseLong(dm.group(1)));
        if (containsAny(msg, "上周", "上星期", "上个星期")) return today.minusWeeks(1);
        // 日记/体重不允许记到未来：出现“明天/后天/N天后”一律按今天处理
        return today;
    }

    private boolean isQueryTodayIntent(String msg) {
        if (isWeekSummaryIntent(msg)) return false;
        if (isWeightQueryIntent(msg)) return false;
        if (containsAny(msg, "冰箱", "食材", "推荐", "吃什么", "做什么", "怎么吃", "食谱", "菜谱")) return false;
        // 同时出现“今天/今日”和“记录/摄入/吃了多少”等词，才视为今日汇总，
        // 避免“看看”这类宽泛词把无关消息吞进来
        if (containsAny(msg, "今天", "今日") && containsAny(msg, "吃了多少", "记录", "摄入", "数据", "汇总", "总共", "吃了什么", "吃了啥")) {
            return true;
        }
        return containsAny(msg, "没看到", "没记录", "没写", "今天记录", "昨日吃", "今日吃",
                "吃了啥", "吃了什么", "吃了多少", "记了什么", "记了啥", "今日数据",
                "查进度", "进度查询", "我今天吃", "我今天记录", "今天总共", "今日总共", "今日汇总");
    }

    private boolean isWeekSummaryIntent(String msg) {
        if (isWeeklyPlanIntent(msg)) return false;
        return containsAny(msg, "这周", "本周", "这几天", "最近几天", "本周汇总", "最近汇总",
                "上周", "近几天", "近一周", "最近一周", "7天", "七天", "一周", "周总结", "周汇总",
                "近7天", "周统计", "上周汇总", "上周总结");
    }

    private boolean isWeeklyPlanIntent(String msg) {
        return containsAny(msg, "一周食谱", "一周计划", "一周菜单", "一周的食谱", "一周的菜",
                "七天食谱", "7天食谱", "整周食谱", "这周食谱", "本周食谱", "规划一周", "安排一周",
                "一周三餐", "七天三餐", "一周的餐");
    }

    private boolean isWeightRecordIntent(String msg) {
        if (tryExtractWeight(msg) == null) return false;
        // 带明确体重值但语气是提问（多少 / 几斤 / 吗 / ？）时，仍按查询处理
        return !containsAny(msg, "多少", "几斤", "几公斤", "多重", "吗", "?", "？", "查", "看看");
    }

    private boolean isWeightQueryIntent(String msg) {
        return containsAny(msg, "体重", "称了吗", "我重", "几斤了", "体重曲线", "体重历史",
                "我现在多少", "我现在体重", "看看体重", "最近体重", "体重变化", "体重记录",
                "我重了", "我瘦了", "现在多重", "现在多少斤", "现在多少kg");
    }

    private boolean isFridgeListIntent(String msg) {
        return containsAny(msg, "冰箱里有什么", "冰箱里有啥", "冰箱里现在", "冰箱还有啥", "冰箱有什么",
                "冰箱有啥", "看看冰箱", "查冰箱", "看看我家有啥", "家里还有什么", "家里有啥", "我家有啥");
    }

    private boolean isFridgeClearIntent(String msg) {
        return containsAny(msg, "清空冰箱", "冰箱都吃完了", "冰箱空了", "扔掉冰箱", "清空所有食材");
    }

    private boolean isFridgeRemoveIntent(String msg) {
        if (containsAny(msg, "冰箱里没有", "冰箱没有", "我家没有", "没有这个", "扔了", "扔掉")) return true;
        // “吃完了/吃光了”只有在不像描述一餐时才当作“从冰箱移除”
        if (containsAny(msg, "吃完了", "吃光了")) {
            return !containsAny(msg, "午饭", "晚餐", "早餐", "午餐", "晚饭", "早饭", "夜宵",
                    "这顿", "一顿", "饭", "菜", "面", "粥");
        }
        return false;
    }

    private boolean isPreferenceIntent(String msg) {
        if (isFridgeAddIntent(msg)) return false;
        return containsAny(msg, "喜欢吃", "爱吃", "特别喜欢", "超爱", "最爱吃", "很喜欢",
                "不喜欢吃", "不爱吃", "不喜欢", "讨厌吃", "讨厌", "过敏", "忌口", "不能吃", "吃不了", "不吃",
                "怕辣", "怕腥", "偏好", "口味", "喜欢", "不太行", "不太爱吃", "不接受",
                "碰都不碰", "不要放", "不加");
    }

    private boolean isUndoLastDiaryIntent(String msg) {
        return containsAny(msg, "撤销", "撤回", "删掉刚才", "删掉刚刚", "取消上一条",
                "取消刚才", "我不加了", "刚才不算", "刚那条不算", "那条不算", "上一条撤回",
                "上一条撤销", "删掉上一条", "删掉最后一条", "取消刚刚", "不要刚才的",
                "撤回上一条", "撤回刚刚的", "刚才记错了", "记错了", "刚才那条删掉");
    }

    private boolean isFoodDiaryIntent(String msg) {
        boolean trigger = containsAny(msg, "吃了", "吃了一顿", "一顿吃", "今日份", "早餐吃", "午餐吃", "晚餐吃",
                "今天吃了", "刚吃", "计入日记", "记日记", "记一下饮食", "加一条日记",
                "打卡", "登记", "记录一下", "我吃了", "我刚吃", "报一下", "报一下今天",
                "报一下我吃", "登记一下", "记一下今天吃的");
        return trigger || looksLikeFoodList(msg);
    }

    private boolean isGapIntent(String msg) {
        return containsAny(msg, "缺口", "营养缺口", "我缺什么", "哪些没达标", "达标了吗", "我的营养", "需要补什么",
                "营养分析", "我的雷达", "营养雷达", "营养评估", "健康评估", "缺啥", "缺什么", "该补点啥", "该补什么",
                "达标", "营养够不够", "缺不缺", "营养够", "营养均衡", "营养状况");
    }

    private boolean isReRecommendIntent(String msg) {
        return containsAny(msg, "换一批", "再来一批", "换几道", "换几个", "换别的", "换点别的", "换个花样",
                "重新推荐", "重新来", "再推荐", "再来点", "其他的", "不要这个", "不喜欢这个", "换一道");
    }

    private boolean isRecommendIntent(String msg) {
        // 食谱引用（拖拽/挂载的食谱标签内容，如「🍳 食谱「番茄鸡蛋盖浇饭」」）不是推荐请求
        if (matchesAny(msg, "食谱「", "食谱“")) return false;
        return containsAny(msg, "推荐", "吃什么好", "今晚吃什么", "做什么菜", "食谱", "推荐菜", "建议吃什么",
                "给我点", "有没有", "来点", "想做菜", "想做", "我想吃", "想吃点什么", "吃啥", "今天吃啥",
                "推荐一下", "来一个", "来几个", "给我推荐", "健康做法", "减脂餐", "减脂食谱", "增肌餐",
                "增肌食谱", "低糖", "低油", "低脂", "清淡", "好吃的", "简单菜", "快手菜", "家常菜");
    }

    private boolean isProgressIntent(String msg) {
        return containsAny(msg, "进度", "还能吃多少", "今日反馈", "今天还能", "剩下多少", "配额", "还差多少",
                "还差", "还剩多少", "剩余配额", "剩余额度", "还能吃", "今日额度", "还多少", "今天还可以",
                "我还能吃多少", "吃多了吗", "吃少了吗", "超了吗", "还需要", "还缺多少");
    }

    private boolean isProfileUpdateIntent(String msg) {
        return containsAny(msg, "我是", "我要减肥", "要减脂", "要增肌", "要塑形", "要长高", "要变重",
                "目标是", "身高", "年龄", "我岁", "我高");
    }

    private boolean isHelpIntent(String msg) {
        return containsAny(msg, "你能做什么", "你会什么", "帮助", "help", "怎么用", "功能", "能干啥");
    }

    /** 餐别统一成中文，兼容 AI 返回的 breakfast/lunch/dinner/snack 与前端下拉的中文值 */
    public static String normalizeMealType(String raw) {
        return com.nutriai.dto.FoodDiaryDto.normalizeMealType(raw);
    }

    private boolean isFridgeAddIntent(String msg) {
        if (containsAny(msg, "别管", "不用管", "不管", "忽略", "别用", "不要用", "别考虑", "不用考虑",
                "清空", "移除", "删掉", "换一批", "再来一批", "重新推荐", "换别的",
                "吃什么", "做什么", "怎么做", "推荐", "菜谱", "食谱")) return false;
        if (!containsAny(msg, "我家有", "家里有", "家里还有", "厨房有", "厨房里有", "冰箱有", "冰箱里有",
                "食材有", "买了", "新买了", "刚买了", "又买了", "剩下有", "现在有")) return false;
        return !splitIngredients(msg).isEmpty();
    }

    private static final java.util.regex.Pattern QTY_PATTERN = java.util.regex.Pattern.compile(
            "^\\s*\\d+(?:\\.\\d+)?\\s*(?:g|G|克|kg|KG|公斤|千克|斤|两|个|只|颗|盒|包|瓶|袋|把|块|片|根|杯|碗|勺|ml|毫升|升)\\s*",
            java.util.regex.Pattern.CASE_INSENSITIVE);

    private List<String> splitIngredients(String msg) {
        String s = msg.replaceAll("^.*?(?:我家有|家里有|家里还有|冰箱有|冰箱里有|厨房里有|厨房有|食材有|新买了|刚买了|又买了|买了)[是：:]?\\s*", "");
        s = s.replaceAll("(?:还有?|以及|以及有|加上|又买了|新买了|刚买了)", "、");
        s = s.replaceAll("(?:了|吧|呀|啊|哦|呢|嘛)\\s*$", "");
        // 去掉句尾的意图描述，如“，今晚吃什么”
        s = s.replaceAll("[，,、\\s]*(?:今晚|今天|明天)?\\s*(?:吃什么|做什么|怎么做|推荐.*|来点.*|做点什么).*$", "");
        String[] arr = s.split("[，,、;；\\s]+|(?:和|跟|与)(?=[\\u4e00-\\u9fa5])");
        List<String> list = new ArrayList<>();
        for (String x : arr) {
            String t = x.trim().replaceAll("^[。.!！?？\\.]+|[。.!！?？\\.]+$", "");
            // 去掉开头的数量单位：“200g 鸡胸肉” → “鸡胸肉”
            t = QTY_PATTERN.matcher(t).replaceFirst("");
            // 去掉结尾的数量单位：“鸡胸肉 200g” → “鸡胸肉”
            t = t.replaceAll("\\s*\\d+(?:\\.\\d+)?\\s*(?:g|G|克|kg|KG|公斤|千克|斤|两|个|只|颗|盒|包|瓶|袋|把|块|片|根|杯|碗|勺|ml|毫升|升)\\s*$", "");
            if (t.isBlank()) continue;
            if (t.matches("\\d+(?:\\.\\d+)?")) continue;
            if (t.length() >= 1 && t.length() <= 12 && !isGeneric(t) && !isFridgeNoise(t)) list.add(t);
        }
        return list;
    }

    private boolean isFridgeNoise(String s) {
        Set<String> noise = Set.of(
                "换一批", "别管", "不管", "忽略", "别用", "不要用", "冰箱", "存货", "食材", "推荐",
                "换别的", "别的", "其他", "再来", "重新", "一批", "一下", "什么", "怎么", "可以",
                "帮我", "给我", "今晚", "今天", "明天", "晚饭", "午饭", "早餐", "我的", "这些", "那些"
        );
        if (noise.contains(s)) return true;
        return s.matches(".*(?:换一批|别管|忽略|存货|推荐|一批).*");
    }

    private static final java.util.regex.Pattern DISLIKE_CUE = java.util.regex.Pattern.compile(
            "(?:不喜欢|不爱吃|讨厌|过敏|忌口|不能吃|吃不了|怕|不吃|不接受|不要|别放|不加|碰都不碰|不太爱|不太行)");
    private static final java.util.regex.Pattern LIKE_CUE = java.util.regex.Pattern.compile(
            "(?:喜欢吃|爱吃|特别喜欢|超爱|最爱吃|很喜欢|喜欢|偏好|爱)");

    private List<String[]> parsePreferences(String msg) {
        List<String[]> result = new ArrayList<>();
        // 转折词（但/不过/然而）会把“喜欢 A 讨厌 B”拆成两个独立判断
        String[] clauses = msg.split("[，,。.；;！!？?\\n]+|(?:但是|但|不过|然而|可是|而是)");
        for (String clause : clauses) {
            String c = clause.trim();
            if (c.isEmpty()) continue;
            boolean dislike = DISLIKE_CUE.matcher(c).find();
            boolean like = !dislike && LIKE_CUE.matcher(c).find();
            if (!dislike && !like) continue;
            // 同一句里可能用“和/跟/与/、/以及/还有”并列多个食物
            String[] foods = c.split("(?:以及|还有|和|跟|与|、)");
            for (String part : foods) {
                String food = extractPreferenceFood(part);
                if (food == null) continue;
                result.add(new String[]{food, dislike ? "DISLIKE" : "LIKE"});
            }
        }
        if (result.isEmpty()) {
            boolean dislike = DISLIKE_CUE.matcher(msg).find();
            boolean like = !dislike && LIKE_CUE.matcher(msg).find();
            if (dislike || like) {
                String food = extractPreferenceFood(msg);
                if (food != null) result.add(new String[]{food, dislike ? "DISLIKE" : "LIKE"});
            }
        }
        return result;
    }

    private String extractPreferenceFood(String msg) {
        List<String> stop = List.of(
                "吃的东西", "特别喜欢吃", "喜欢吃", "不爱吃", "讨厌吃", "不能吃", "吃不了",
                "爱吃", "喜欢", "不喜欢", "讨厌", "过敏", "忌口", "怕辣", "怕腥", "怕",
                "这个推荐", "推荐", "这个", "那个", "我比较", "比较", "很", "非常", "特别", "真的",
                "有点", "还算", "有些", "稍微", "极度", "超", "挺", "就是", "尤其", "更加",
                "碰都不碰", "不接受", "不要放", "不加", "不吃", "不要", "别放", "不太爱吃", "不太行",
                "偏好", "口味", "东西", "食物"
        );
        String s = msg;
        for (String x : stop) s = s.replace(x, " ");
        s = s.replaceAll("[，,。.!！?？~～、;；]+", " ").trim();
        if (s.isBlank()) return null;
        Pattern p = Pattern.compile("[\\u4e00-\\u9fa5A-Za-z]{1,16}");
        Matcher m = p.matcher(s);
        List<String> candidates = new ArrayList<>();
        while (m.find()) candidates.add(m.group());
        candidates.sort((a, b) -> Integer.compare(b.length(), a.length()));
        for (String g : candidates) {
            String cleaned = g;
            for (int i = 0; i < 3; i++) {
                String before = cleaned;
                cleaned = cleaned.replaceAll("^(?:我|你|他|她|它|吃|喝|来|要|做|对|跟|和|特别|比较|很|超|挺)+", "")
                        .replaceAll("(?:了|吧|呀|啊|哦|呢|嘛)$", "");
                if (cleaned.equals(before)) break;
            }
            if (cleaned.isBlank() || isGeneric(cleaned)) continue;
            if (cleaned.matches(".*[我你他她它].*")) continue;
            if (cleaned.length() > 12) continue;
            return cleaned;
        }
        return null;
    }

    private boolean isGeneric(String s) {
        Set<String> gen = Set.of(
                "我", "你", "他", "她", "它", "菜", "食物", "饭", "东西", "吃的",
                "这个", "那个", "今天", "昨天", "现在", "比较", "很", "非常",
                "特别", "真的", "有点", "还算", "有些", "稍微", "极度", "超", "挺",
                "有", "了", "吗", "呢", "啊", "吧", "的", "在", "是", "要"
        );
        return gen.contains(s);
    }

    private String extractFirstNoun(String msg) {
        Pattern p = Pattern.compile("[\\u4e00-\\u9fa5A-Za-z]{2,10}");
        Matcher m = p.matcher(msg);
        while (m.find()) {
            String g = m.group();
            if (!isGeneric(g)) return g;
        }
        return null;
    }

    /** 优先把用户的话匹配到冰箱里已有的条目，匹配不到再退回抽取名词。 */
    private String findFridgeItem(Long userId, String msg) {
        try {
            for (String item : fridgeService.list(userId)) {
                if (item != null && !item.isBlank() && msg.contains(item)) return item;
            }
        } catch (Exception ignored) {
        }
        String cleaned = msg.replaceAll(".*(?:冰箱里没有|冰箱没有|我家没有|没有这个|没有|吃完了|吃光了|扔了|扔掉)[是：:]?", "");
        cleaned = cleaned.replaceAll("(?:了|吧|呀|啊|哦|呢|嘛)\\s*$", "");
        return extractFirstNoun(cleaned);
    }

    private static final Set<String> FOOD_HINTS = new HashSet<>(Arrays.asList(
            "米","饭","面","条","馒","包","粥","蛋","鸡","鸭","鱼","虾","蟹","肉","猪","牛","羊",
            "胡","萝","卜","番","茄","黄","瓜","白","菜","油","菜","菠","菜","西","兰","花","土","豆",
            "豆","青","椒","辣","椒","葱","蒜","姜","奶","茶","咖","啡","果","苹","香","橙","柠","柿",
            "杏","葡","桃","西","瓜","草","水","果","坚","果","核","桃","松","仁","杏","仁","巧","克",
            "糖","饼","干","糕","点","面","包","酱","油","盐","醋","椒","芝","麻","米","花","花","生",
            "玉","米","红","薯","番","薯","南","瓜","冬","瓜","木","耳","银","耳","海","带","紫","菜",
            "酸","菜","泡","菜","火","腿","腊","肉","香","肠","鸡","排","牛","排","汉","堡","披","萨",
            "炸","鸡","薯","条","意","面","通","心","粉","米","线","粉","丝","腐","竹","豆","腐",
            "豆","浆","豆","干","酱","豆","纳","豆","胡","桃","乳","酸","奶","冰","淇","淋",
            "奥","利","奥","可","可","燕","麦","谷","物","片","麦","片","煎","饼","蛋","糕","甜","圈",
            "薯","片","虾","条","鱿","鱼","章","鱼","贝","海","螺","鲍","鱼","瑶","柱",
            "菠","萝","芒","果","樱","桃","荔","枝","龙","眼","石","榴","橄","榄","椰","子","芒","果",
            "豆","沙","红","豆","绿","豆","黄","豆","豌","豆","蚕","豆","扁","豆","芸","豆","莲","子",
            "薏","米","桂","圆","大","枣","枸","杞","银","耳","百","合","山","药","芡","实"
    ));

    private static final Pattern WEIGHT_PATTERN = Pattern.compile("(\\d+(?:\\.\\d+)?)\\s*(g|G|克|份|两|斤|kg|KG|公斤|个|颗|块|片|碗|杯|勺|根|节|只|包|片)");

    private boolean looksLikeFoodList(String msg) {
        String text = msg.trim();
        if (text.length() > 120) return false;
        if (!WEIGHT_PATTERN.matcher(text).find()) return false;
        int hintHits = 0;
        for (String h : FOOD_HINTS) if (text.contains(h)) hintHits++;
        return hintHits >= 2;
    }

    private boolean containsAny(String msg, String... words) {
        for (String w : words) if (msg.contains(w)) return true;
        return false;
    }

    private String buildEmphasizeNotes(java.util.List<java.util.Map<String, String>> tags) {
        if (tags == null || tags.isEmpty()) return null;
        java.util.List<String> like = new java.util.ArrayList<>();
        java.util.List<String> dislike = new java.util.ArrayList<>();
        java.util.List<String> fridge = new java.util.ArrayList<>();
        java.util.List<String> gap = new java.util.ArrayList<>();
        java.util.List<String> custom = new java.util.ArrayList<>();
        for (java.util.Map<String, String> t : tags) {
            String kind = t.getOrDefault("kind", "");
            String name = t.getOrDefault("name", "");
            if (name == null || name.isBlank()) continue;
            switch (kind) {
                case "likes" -> like.add(name);
                case "dislikes" -> dislike.add(name);
                case "fridge" -> fridge.add(name);
                case "gap" -> gap.add(name);
                default -> custom.add(name);
            }
        }
        StringBuilder sb = new StringBuilder();
        if (!like.isEmpty()) sb.append("请优先推荐/包含：").append(String.join("、", like)).append("；");
        if (!dislike.isEmpty()) sb.append("请避开/不要推荐：").append(String.join("、", dislike)).append("；");
        if (!fridge.isEmpty()) sb.append("可利用的已有食材：").append(String.join("、", fridge)).append("；");
        if (!gap.isEmpty()) sb.append("重点关注营养缺口：").append(String.join("、", gap)).append("；");
        if (!custom.isEmpty()) sb.append("其他要求：").append(String.join("、", custom)).append("；");
        if (sb.length() > 0) sb.setLength(sb.length() - 1);
        return sb.toString();
    }
}