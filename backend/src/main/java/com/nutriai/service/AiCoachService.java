package com.nutriai.service;

import com.nutriai.ai.AiService;
import com.nutriai.ai.AiServiceFactory;
import com.nutriai.dto.CoachFeedbackRequest;
import com.nutriai.dto.CoachFeedbackResponse;
import com.nutriai.dto.DailyNutritionDto;
import com.nutriai.dto.FridgeRequest;
import com.nutriai.dto.NutritionGapDto;
import com.nutriai.dto.RecommendationResponse;
import com.nutriai.dto.UserProfileRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 「AI 教练」服务 —— 营养缺口雷达 / 实时反馈 / 冰箱有料
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiCoachService {

    private final AiServiceFactory aiServiceFactory;
    private final FoodDiaryService foodDiaryService;
    private final FoodPreferenceService foodPreferenceService;

    /** 缺口解读缓存：userId → (fingerprint → aiInsight) */
    private final Map<Long, String> gapFingerprintCache = new ConcurrentHashMap<>();
    private final Map<Long, String> gapInsightCache = new ConcurrentHashMap<>();

    /** A：营养缺口雷达 */
    public NutritionGapDto getNutritionGap(Long userId, UserProfileRequest profile) {
        List<DailyNutritionDto> weekly = foodDiaryService.getWeeklySummary(userId, LocalDate.now());
        NutritionGapDto gap = new NutritionGapDto();

        int trackedDays = 0, goalDays = 0;
        double c=0,p=0,cb=0,f=0, cg=0,pg=0,cbg=0,fg=0;
        for (DailyNutritionDto d : weekly) {
            if (d.getTotalItems() > 0) {
                trackedDays++;
                c += nz(d.getTotalCalories()); p += nz(d.getTotalProtein());
                cb += nz(d.getTotalCarbs()); f += nz(d.getTotalFat());
            }
            if (d.getCalorieGoal() != null) {
                goalDays++;
                cg += d.getCalorieGoal(); pg += nz(d.getProteinGoal());
                cbg += nz(d.getCarbsGoal()); fg += nz(d.getFatGoal());
            }
        }
        gap.setTrackedDays(trackedDays);
        if (trackedDays == 0) {
            gap.setAiInsight("近 7 日还没有食物记录，先去「食物日记」记几餐，我就能为你看清营养缺口。");
            return gap;
        }
        gap.setAvgCalories(round(c / trackedDays));
        gap.setAvgProtein(round(p / trackedDays));
        gap.setAvgCarbs(round(cb / trackedDays));
        gap.setAvgFat(round(f / trackedDays));

        if (goalDays > 0) {
            double avgCg = cg / goalDays, avgPg = pg / goalDays, avgCbg = cbg / goalDays, avgFg = fg / goalDays;
            gap.setTargetCalories(round(avgCg));
            gap.setTargetProtein(round(avgPg));
            gap.setTargetCarbs(round(avgCbg));
            gap.setTargetFat(round(avgFg));
            gap.setCalPercent(pct(gap.getAvgCalories(), avgCg));
            gap.setProteinPercent(pct(gap.getAvgProtein(), avgPg));
            gap.setCarbsPercent(pct(gap.getAvgCarbs(), avgCbg));
            gap.setFatPercent(pct(gap.getAvgFat(), avgFg));
        }

        buildItems(gap, "热量", gap.getCalPercent(), gap.getTargetCalories(), false);
        buildItems(gap, "蛋白质", gap.getProteinPercent(), gap.getTargetProtein(), true);
        buildItems(gap, "碳水", gap.getCarbsPercent(), gap.getTargetCarbs(), false);
        buildItems(gap, "脂肪", gap.getFatPercent(), gap.getTargetFat(), false);

        // AI 解读（按记录变更指纹缓存，避免每次切页都烧 token）
        String fp = computeGapFingerprint(gap);
        if (fp.equals(gapFingerprintCache.get(userId)) && gapInsightCache.get(userId) != null) {
            gap.setAiInsight(gapInsightCache.get(userId));
        } else {
            try {
                aiServiceFactory.getService().interpretGap(gap);
            } catch (Exception e) {
                log.warn("[AI教练] 缺口解读异常: {}", e.getMessage());
            }
            if (gap.getAiInsight() == null || gap.getAiInsight().isBlank()) {
                gap.setAiInsight(buildLocalInsight(gap));
            }
            gapFingerprintCache.put(userId, fp);
            gapInsightCache.put(userId, gap.getAiInsight());
        }
        return gap;
    }

    /** AI 不可用时的本地兜底解读，保证面板永远有可读结论。 */
    private String buildLocalInsight(NutritionGapDto gap) {
        List<String> lows = new ArrayList<>();
        List<String> highs = new ArrayList<>();
        for (NutritionGapDto.GapItem item : gap.getDeficiencies()) {
            lows.add(item.getNutrient() + " " + Math.round(item.getPercent()) + "%");
        }
        for (NutritionGapDto.GapItem item : gap.getSurpluses()) {
            highs.add(item.getNutrient() + " " + Math.round(item.getPercent()) + "%");
        }
        if (lows.isEmpty() && highs.isEmpty()) {
            return "近 7 日各大营养素都在合理区间，保持当前饮食结构即可。";
        }
        StringBuilder sb = new StringBuilder("近 7 日");
        if (!lows.isEmpty()) sb.append("偏低：").append(String.join("、", lows));
        if (!lows.isEmpty() && !highs.isEmpty()) sb.append("；");
        if (!highs.isEmpty()) sb.append("偏高：").append(String.join("、", highs));
        sb.append("。优先补足偏低营养素，并适当控制偏高项。");
        return sb.toString();
    }

    private void buildItems(NutritionGapDto gap, String nutrient, Double percent, Double target, boolean proteinLike) {
        if (percent == null || percent <= 0) return;
        NutritionGapDto.GapItem item = new NutritionGapDto.GapItem();
        if (percent < 85) {
            item.setNutrient(nutrient); item.setIssue("偏低"); item.setPercent(percent);
            item.setSuggestion(suggestionFor(nutrient, true, proteinLike));
            gap.getDeficiencies().add(item);
        } else if (percent > 115) {
            item.setNutrient(nutrient); item.setIssue("偏高"); item.setPercent(percent);
            item.setSuggestion(suggestionFor(nutrient, false, proteinLike));
            gap.getSurpluses().add(item);
        }
    }

    private String suggestionFor(String nutrient, boolean low, boolean proteinLike) {
        if (proteinLike) return low ? "优先补充瘦肉、蛋、豆制品、奶制品等优质蛋白" : "减少高蛋白动物性食物，均衡植物蛋白";
        if (nutrient.equals("热量")) return low ? "适当增加一份主食或健康加餐" : "控制总热量，减少精制碳水和油炸";
        if (nutrient.equals("碳水")) return low ? "补充全谷物、薯类、水果等复合碳水" : "减少精制米面糖，用粗粮替代";
        if (nutrient.equals("脂肪")) return low ? "适量补充坚果、牛油果、深海鱼等好脂肪" : "少油少煎炸，多用蒸煮炖";
        return "";
    }

    /** C：AI 教练实时反馈 */
    public CoachFeedbackResponse getCoachFeedback(Long userId, CoachFeedbackRequest req) {
        DailyNutritionDto today = foodDiaryService.getDailySummary(userId != null ? userId : 0L, LocalDate.now());
        CoachFeedbackResponse resp = new CoachFeedbackResponse();

        double todayCal = today == null ? 0 : nz(today.getTotalCalories());
        double goalCal = 0;
        StringBuilder ctx = new StringBuilder();
        if (today != null && today.getTotalItems() > 0) {
            ctx.append(String.format("今天已摄入：热量 %.0f kcal · 蛋白质 %.0f g · 碳水 %.0f g · 脂肪 %.0f g（%d 笔记录）\n",
                    todayCal, nz(today.getTotalProtein()), nz(today.getTotalCarbs()), nz(today.getTotalFat()),
                    today.getTotalItems()));
        } else {
            ctx.append("今天还没有记录任何进食。\n");
            if (userId == null) ctx.append("（未登录，仅按画像给出通用建议）\n");
        }
        // 目标与当天是否有记录无关，避免"设置了目标却提示未设置"
        if (today != null && today.getCalorieGoal() != null) {
            goalCal = today.getCalorieGoal();
        }
        if (req.getLatestMeal() != null && !req.getLatestMeal().isBlank()) {
            ctx.append("用户刚吃了：" + req.getLatestMeal() + "\n");
        }

        double calPercent = goalCal > 0 ? pct(todayCal, goalCal) : 0;
        CoachFeedbackResponse.TodayIntake t = new CoachFeedbackResponse.TodayIntake();
        t.setCalories(round(todayCal));
        if (today != null) {
            t.setProtein(round(nz(today.getTotalProtein())));
            t.setCarbs(round(nz(today.getTotalCarbs())));
            t.setFat(round(nz(today.getTotalFat())));
        }
        t.setCaloriePercent(calPercent);
        resp.setToday(t);

        double remain = goalCal > 0 ? goalCal - todayCal : 0;
        String remainText;
        if (goalCal <= 0) remainText = "设置目标后可查看今日剩余配额";
        else if (remain > 0) remainText = "今日约还可摄入 " + Math.round(remain) + " kcal";
        else remainText = "今日已基本达到/超出热量目标，建议清淡收尾";
        resp.setRemainingSummary(remainText);

        String message = aiServiceFactory.getService()
                .generateCoachFeedback(req.getProfile(), ctx.toString());
        resp.setMessage(message);
        resp.setQuickSuggestions(new ArrayList<>());
        return resp;
    }

    /** D：冰箱有料 */
    public RecommendationResponse getFridgeRecommendation(Long userId, FridgeRequest req) {
        String gapContext = buildWeeklyGapContext(userId);
        String pref = userId != null ? foodPreferenceService.buildPreferenceContext(userId) : "";
        StringBuilder ctx = new StringBuilder();
        if (!gapContext.isBlank()) ctx.append(gapContext);
        if (!pref.isBlank()) { if (ctx.length() > 0) ctx.append("\n"); ctx.append(pref); }
        String notes = req.getNotes();
        if (notes != null && !notes.isBlank()) {
            if (ctx.length() > 0) ctx.append("\n");
            ctx.append("【会话强调·请严格遵守】").append(notes);
        }
        String context = ctx.length() == 0 ? "暂时没有饮食数据，给出均衡的搭配即可。" : ctx.toString();
        return aiServiceFactory.getService().recommendFromFridge(req.getProfile(), req.getIngredients(), context);
    }

    private String buildWeeklyGapContext(Long userId) {
        if (userId == null) return "";
        try {
            List<DailyNutritionDto> weekly = foodDiaryService.getWeeklySummary(userId, LocalDate.now());
            int days = 0; double c=0,p=0,cb=0,f=0;
            for (DailyNutritionDto d : weekly) {
                if (d.getTotalItems() > 0) {
                    days++;
                    c += nz(d.getTotalCalories()); p += nz(d.getTotalProtein());
                    cb += nz(d.getTotalCarbs()); f += nz(d.getTotalFat());
                }
            }
            if (days == 0) return "";
            return String.format("用户近7日日均摄入约 %.0f kcal · 蛋白质 %.0f g · 碳水 %.0f g · 脂肪 %.0f g。",
                    c/days, p/days, cb/days, f/days);
        } catch (Exception e) {
            return "";
        }
    }

    private double nz(Double d) { return d == null ? 0 : d; }
    private double round(double v) { return Math.round(v * 10) / 10.0; }
    private double pct(double a, double b) { return b <= 0 ? 0 : Math.round(a / b * 100); }

    /** 按缺口数据本身生成指纹——只要日均摄入没变，就不重调 LLM */
    private String computeGapFingerprint(NutritionGapDto gap) {
        return String.format("%d|%.0f|%.0f|%.0f|%.0f|%.0f|%.0f|%.0f|%.0f",
                gap.getTrackedDays(),
                nz(gap.getAvgCalories()), nz(gap.getAvgProtein()),
                nz(gap.getAvgCarbs()), nz(gap.getAvgFat()),
                nz(gap.getCalPercent()), nz(gap.getProteinPercent()),
                nz(gap.getCarbsPercent()), nz(gap.getFatPercent()));
    }
}