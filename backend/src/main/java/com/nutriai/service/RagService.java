package com.nutriai.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service
public class RagService {

    private final ObjectMapper objectMapper;

    private List<GuidelineEntry> guidelines = List.of();
    private List<FoodEntry> foodDb = List.of();

    private static final int TOP_K_GUIDELINES = 3;
    private static final int TOP_K_FOODS = 5;

    public RagService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @PostConstruct
    void init() {
        loadGuidelines();
        loadFoodDb();
        log.info("[RAG] Loaded: {} guidelines, {} foods", guidelines.size(), foodDb.size());
    }

    private void loadGuidelines() {
        try (InputStream is = new ClassPathResource("rag/dietary_guidelines.json").getInputStream()) {
            String json = new String(is.readAllBytes(), StandardCharsets.UTF_8);
            guidelines = objectMapper.readValue(json, new TypeReference<List<GuidelineEntry>>() {});
        } catch (Exception e) {
            log.error("[RAG] Guidelines load failed: {}", e.getMessage());
        }
    }

    private void loadFoodDb() {
        try (InputStream is = new ClassPathResource("rag/food_nutrition.json").getInputStream()) {
            String json = new String(is.readAllBytes(), StandardCharsets.UTF_8);
            foodDb = objectMapper.readValue(json, new TypeReference<List<FoodEntry>>() {});
        } catch (Exception e) {
            log.error("[RAG] Food DB load failed: {}", e.getMessage());
        }
    }

    public String retrieveGuidelines(String query) {
        if (guidelines.isEmpty() || query == null || query.isBlank()) return "";
        List<GuidelineEntry> scored = new ArrayList<>(guidelines);
        scored.sort((a, b) -> Double.compare(score(b, query), score(a, query)));
        StringBuilder sb = new StringBuilder();
        int count = 0;
        for (GuidelineEntry g : scored) {
            double s = score(g, query);
            if (s <= 0) break;
            sb.append("[").append(g.title).append("] ").append(g.content).append("\n");
            count++;
            if (count >= TOP_K_GUIDELINES) break;
        }
        return sb.toString();
    }

    public String retrieveFoods(String query) {
        if (foodDb.isEmpty() || query == null || query.isBlank()) return "";
        List<FoodEntry> scored = new ArrayList<>(foodDb);
        scored.sort((a, b) -> Double.compare(score(b, query), score(a, query)));
        StringBuilder sb = new StringBuilder();
        int count = 0;
        for (FoodEntry f : scored) {
            double s = score(f, query);
            if (s <= 0) break;
            sb.append(formatFood(f));
            count++;
            if (count >= TOP_K_FOODS) break;
        }
        return sb.toString();
    }

    public FoodEntry lookupFood(String foodName) {
        if (foodDb.isEmpty() || foodName == null) return null;
        String lower = foodName.toLowerCase().trim();
        for (FoodEntry f : foodDb) {
            if (f.name.equals(foodName) || f.name.equals(lower)) return f;
            if (f.aliases != null) {
                for (String alias : f.aliases) {
                    if (alias.equalsIgnoreCase(foodName) || alias.contains(lower) || lower.contains(alias.toLowerCase())) {
                        return f;
                    }
                }
            }
        }
        return null;
    }

    public List<FoodEntry> lookupFoods(List<String> names) {
        if (names == null || names.isEmpty()) return List.of();
        return names.stream()
                .map(this::lookupFood)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
    }

    public String buildInjectedContext(String userQuery) {
        if (userQuery == null || userQuery.isBlank()) return "";
        StringBuilder sb = new StringBuilder();

        String foodInfo = retrieveFoods(userQuery);
        if (!foodInfo.isBlank()) {
            sb.append("\n=== Food Nutrition Data (authoritative, do not fabricate) ===\n");
            sb.append(foodInfo);
        }

        String guidelineInfo = retrieveGuidelines(userQuery);
        if (!guidelineInfo.isBlank()) {
            sb.append("\n=== Dietary Guidelines (cite source when referencing) ===\n");
            sb.append(guidelineInfo);
        }

        return sb.toString();
    }

    public String buildFoodRecognitionHint(String description) {
        String foodInfo = retrieveFoods(description);
        if (foodInfo.isBlank()) return "";
        return "\n=== Authoritative food data (per 100g), estimate proportionally ===\n" + foodInfo
                + "\nCalculate actual intake proportionally based on the serving size described by user.\n";
    }

    /**
     * Try to recognize food from local DB and compute nutrition scaled to actual serving size.
     * Returns a Map on hit, null on miss.
     * Map keys: foodName, mealType, calories, proteinGrams, carbsGrams, fatGrams, servingSize, source
     */
    public Map<String, Object> tryLocalRecognize(String description) {
        if (description == null || description.isBlank()) return null;

        // Units regex: match quantity + Chinese/English unit
        Matcher qm = Pattern.compile(
            "^([\\d]+(?:\\.[\\d]+)?)\\s*" +
            "(g|kg|ml|\\u514b|\\u516C\\u65A4|\\u65A4|\\u4E24|\\u6BEB\\u5347|\\u5347|\\u4E2A|\\u53EA|\\u4EFD|\\u7897|\\u76D8|\\u676F|\\u52FA|\\u7897\\u996D)")
            .matcher(description.trim());
        double quantity = 100;
        String unit = "g";
        String foodPart;
        if (qm.find()) {
            try { quantity = Double.parseDouble(qm.group(1)); } catch (NumberFormatException ignored) {}
            unit = qm.group(2);
            foodPart = description.substring(qm.end()).trim();
        } else {
            foodPart = description.trim();
        }

        double gramEquivalent = toGram(quantity, unit);
        FoodEntry entry = lookupFood(foodPart);
        if (entry == null) {
            entry = lookupFood(description);
            if (entry == null) return null;
        }

        double ratio = gramEquivalent / 100.0;
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("foodName", entry.name);
        result.put("mealType", guessMealType(entry));
        result.put("calories", round2(entry.per100g.getOrDefault("calories", 0.0) * ratio));
        result.put("proteinGrams", round2(entry.per100g.getOrDefault("protein", 0.0) * ratio));
        result.put("carbsGrams", round2(entry.per100g.getOrDefault("carbs", 0.0) * ratio));
        result.put("fatGrams", round2(entry.per100g.getOrDefault("fat", 0.0) * ratio));
        result.put("servingSize", gramEquivalent);
        result.put("source", "local-db");
        return result;
    }

    private double toGram(double qty, String unit) {
        return switch (unit) {
            case "kg", "\u516C\u65A4" -> qty * 1000;
            case "\u65A4" -> qty * 500;
            case "\u4E24" -> qty * 50;
            case "ml", "\u6BEB\u5347" -> qty;
            case "\u5347" -> qty * 1000;
            case "\u4E2A", "\u53EA" -> qty * 100;
            case "\u4EFD" -> qty * 200;
            case "\u7897", "\u7897\u996D" -> qty * 250;
            case "\u76D8" -> qty * 300;
            case "\u676F" -> qty * 250;
            case "\u52FA" -> qty * 10;
            default -> qty;
        };
    }

    private String guessMealType(FoodEntry f) {
        String cat = f.category;
        if (cat == null) return "snack";
        return switch (cat) {
            case "\u4E3B\u98DF" -> "lunch";
            case "\u8089\u7C7B", "\u8C46\u5236\u54C1", "\u5BB6\u5E38\u83DC" -> "lunch";
            case "\u852C\u83DC", "\u6C34\u679C" -> "snack";
            case "\u4E73\u5236\u54C1" -> "breakfast";
            case "\u575A\u679C" -> "snack";
            default -> "snack";
        };
    }

    private double round2(double v) {
        return Math.round(v * 10) / 10.0;
    }

    // 口语词 → 规范主题词：把用户口语对齐到指南/食物库的规范表述，提升中文召回
    private static final Map<String, String> SYNONYMS = Map.ofEntries(
            Map.entry("减肥", "减脂"), Map.entry("瘦身", "减脂"), Map.entry("变瘦", "减脂"),
            Map.entry("增重", "增重"), Map.entry("增肥", "增重"), Map.entry("长肉", "增重"),
            Map.entry("偏瘦", "增重"), Map.entry("太瘦", "增重"),
            Map.entry("控糖", "糖尿病"), Map.entry("降糖", "糖尿病"), Map.entry("血糖高", "糖尿病"),
            Map.entry("糖尿病", "糖尿病"), Map.entry("血糖", "糖尿病"),
            Map.entry("喝水", "饮水"), Map.entry("补水", "饮水"),
            Map.entry("补钙", "钙"), Map.entry("缺钙", "钙"), Map.entry("骨质", "钙"),
            Map.entry("补铁", "铁"), Map.entry("缺铁", "铁"), Map.entry("贫血", "铁"),
            Map.entry("补蛋白", "蛋白质"), Map.entry("胆固醇", "高血脂"), Map.entry("血脂", "高血脂"),
            Map.entry("痛风", "高尿酸"), Map.entry("尿酸", "高尿酸"), Map.entry("降尿酸", "高尿酸"),
            Map.entry("吃素", "素食"), Map.entry("素食", "素食"),
            Map.entry("断食", "轻断食"), Map.entry("轻断食", "轻断食"),
            Map.entry("便秘", "膳食纤维"), Map.entry("高血压", "低盐"), Map.entry("降血压", "低盐"),
            Map.entry("清淡", "少盐"), Map.entry("控油", "少油")
    );

    private double score(GuidelineEntry g, String query) {
        String title = g.title == null ? "" : g.title;
        String category = g.category == null ? "" : g.category;
        String content = g.content == null ? "" : g.content;
        double score = 0;

        for (Map.Entry<String, String> e : SYNONYMS.entrySet()) {
            if (!query.contains(e.getKey())) continue;
            String norm = e.getValue();
            if (title.contains(norm) || category.contains(norm)) score += 3;
            else if (content.contains(norm)) score += 1.5;
        }
        if (!category.isBlank() && query.contains(category)) score += 2;
        score += keywordHits(title, query);
        return score;
    }

    private double score(FoodEntry f, String query) {
        String name = f.name == null ? "" : f.name;
        String category = f.category == null ? "" : f.category;
        double score = 0;

        if (!name.isBlank() && query.contains(name)) score += 4;
        else if (query.length() >= 2 && !name.isBlank() && name.contains(query)) score += 2.5;

        if (f.aliases != null) {
            for (String a : f.aliases) {
                if (a.isBlank()) continue;
                if (query.contains(a)) score += 3;
                else if (query.length() >= 2 && a.contains(query)) score += 2;
            }
        }
        if (!category.isBlank() && query.contains(category)) score += 2;
        score += keywordHits(name, query);
        return score;
    }

    /** 统计 doc 中有多少个 2-4 字中文片段出现在 query 里（子串匹配，替代失效的中文分词） */
    private double keywordHits(String doc, String query) {
        double hit = 0;
        Set<String> seen = new HashSet<>();
        for (String seg : segments(doc)) {
            if (seg.length() < 2 || seg.length() > 4) continue;
            if (seen.add(seg) && query.contains(seg)) hit += 1;
        }
        return hit;
    }

    private Set<String> segments(String text) {
        Set<String> segs = new HashSet<>();
        if (text == null || text.isBlank()) return segs;
        for (String part : text.split("[^\\p{L}\\p{N}]+")) {
            if (part.length() < 2) continue;
            for (int len = 2; len <= Math.min(4, part.length()); len++) {
                for (int i = 0; i + len <= part.length(); i++) {
                    segs.add(part.substring(i, i + len));
                }
            }
        }
        return segs;
    }

    private String formatFood(FoodEntry f) {
        return String.format("- %s (100g): cal%.0f, pro%.1fg, carb%.1fg, fat%.1fg, fiber%.1fg\n",
                f.name, f.per100g.get("calories"), f.per100g.get("protein"),
                f.per100g.get("carbs"), f.per100g.get("fat"), f.per100g.get("fiber"));
    }

    // --- JSON mapping classes ---

    public static class GuidelineEntry {
        public String id;
        public String category;
        public String title;
        public String content;
    }

    public static class FoodEntry {
        public String name;
        public List<String> aliases = List.of();
        public Map<String, Double> per100g = Map.of();
        public String category;

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof FoodEntry that)) return false;
            return Objects.equals(name, that.name);
        }

        @Override
        public int hashCode() {
            return Objects.hash(name);
        }
    }
}