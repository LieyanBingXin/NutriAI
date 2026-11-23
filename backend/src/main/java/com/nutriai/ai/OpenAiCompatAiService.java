package com.nutriai.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nutriai.config.NutriAiProperties;
import com.nutriai.dto.FoodRecognitionResult;
import com.nutriai.dto.NutritionGapDto;
import com.nutriai.dto.RecommendationResponse;
import com.nutriai.dto.UserProfileRequest;
import com.nutriai.exception.AiApiException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Flux;

import com.nutriai.service.RagService;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Component("openAiCompatAiService")
public class OpenAiCompatAiService implements AiService {

    private final NutriAiProperties properties;
    private final WebClient.Builder webClientBuilder;
    private final ObjectMapper objectMapper;
    private final RagService ragService;

    public OpenAiCompatAiService(NutriAiProperties properties, WebClient.Builder webClientBuilder,
                                  ObjectMapper objectMapper, RagService ragService) {
        this.properties = properties;
        this.webClientBuilder = webClientBuilder;
        this.objectMapper = objectMapper;
        this.ragService = ragService;
    }

    @Override
    public RecommendationResponse analyzeAndRecommend(UserProfileRequest profile, String foodDiaryContext) {
        if (!isAvailable()) {
            throw new IllegalStateException("AI API 未配置: " + properties.getAi().getProvider());
        }

        String endpoint = properties.getAi().getApiEndpoint();
        String model = properties.getAi().getModel();
        String apiKey = properties.getAi().getApiKey();

        log.info("[OpenAiCompat] 调用 AI: endpoint={}, model={}", endpoint, model);

        WebClient client = buildClient(endpoint, apiKey);
        String prompt = buildPrompt(profile, foodDiaryContext);

        Map<String, Object> requestBody = Map.of(
                "model", model,
                "messages", List.of(
                        Map.of("role", "system", "content", SYSTEM_PROMPT),
                        Map.of("role", "user", "content", prompt)
                ),
                "temperature", 0.7,
                "max_tokens", 3000
        );

        String rawResponse;
        try {
            rawResponse = client.post()
                    .uri("/chat/completions")
                    .bodyValue(requestBody)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block(Duration.ofSeconds(120));
        } catch (WebClientResponseException e) {
            log.error("[OpenAiCompat] AI 接口返回 {}: {}", e.getStatusCode().value(), e.getResponseBodyAsString());
            throw new AiApiException("openai-compat", "AI 服务返回错误 " + e.getStatusCode().value()
                    + "，请检查 API Key 或稍后重试");
        } catch (Exception e) {
            log.error("[OpenAiCompat] AI 接口调用失败: {}", e.getMessage());
            throw new AiApiException("openai-compat", "AI 服务连接失败，请稍后重试", e);
        }

        RecommendationResponse response = parseResponse(rawResponse);
        response.setAiProvider(properties.getAi().getProvider());
        response.setGeneratedAt(System.currentTimeMillis());
        return response;
    }

    private static final String STREAM_SYSTEM_PROMPT = """
            你是资深营养师AI助手，拥有丰富的临床营养学经验。请用友好、自然、温暖的中文语气，根据用户信息和近期饮食数据给出全面分析。

            重要：请使用 Markdown 格式输出，确保内容有清晰的结构和分段：
            - 用 ## 表示二级标题（如 ## 用户健康评估、## 近期饮食分析、## 推荐食谱、## 个性化饮食建议）
            - 用 ### 表示三级标题（如 ### 食谱一：菜名）
            - 用 - 开头表示无序列表
            - 不同段落之间空一行
            - 不要输出 JSON 代码块

            回答必须包含以下四个部分：
            1. 用户健康评估（BMI、基础代谢、综合评估）
            2. 近期饮食分析（如果提供了饮食数据，分析达标情况和问题）
            3. 推荐食谱（2-3个，优先推荐用户所在地区的特色菜）
            4. 个性化饮食建议（3-5条，结合饮食数据给出针对性建议）

            语言简洁实用，每个部分要有足够的细节。如果用户有食物过敏/忌讳，务必在建议中避开。
            """;

    @Override
    public Flux<String> streamAndRecommend(UserProfileRequest profile, String foodDiaryContext) {
        if (!isAvailable()) {
            return Flux.error(new IllegalStateException("AI API 未配置"));
        }

        String endpoint = properties.getAi().getApiEndpoint();
        String model = properties.getAi().getModel();
        String apiKey = properties.getAi().getApiKey();

        log.info("[OpenAiCompat] 流式调用: endpoint={}, model={}", endpoint, model);

        WebClient client = buildClient(endpoint, apiKey);
        String prompt = buildStreamPrompt(profile, foodDiaryContext);

        Map<String, Object> requestBody = Map.of(
                "model", model,
                "stream", true,
                "messages", List.of(
                        Map.of("role", "system", "content", STREAM_SYSTEM_PROMPT),
                        Map.of("role", "user", "content", prompt)
                ),
                "temperature", 0.8,
                "max_tokens", 3000
        );

        return client.post()
                .uri("/chat/completions")
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.TEXT_EVENT_STREAM)
                .bodyValue(requestBody)
                .retrieve()
                .bodyToFlux(String.class)
                .mapNotNull(this::extractContent)
                .doOnError(e -> log.error("[OpenAiCompat] 流式错误: {}", e.getMessage()))
                .doOnComplete(() -> log.info("[OpenAiCompat] 流式完成"));
    }

    private String extractContent(String raw) {
        if (raw == null) return null;

        String jsonStr = raw;

        if (jsonStr.startsWith("data:")) {
            int sep = jsonStr.indexOf("\n\n");
            if (sep < 0) sep = jsonStr.indexOf("\r\n\r\n");
            if (sep >= 0) jsonStr = jsonStr.substring(0, sep);
            jsonStr = jsonStr.substring(5);
        }

        jsonStr = jsonStr.trim();

        if (jsonStr.isEmpty() || "[DONE]".equals(jsonStr)) return null;
        if (!jsonStr.startsWith("{")) return null;

        try {
            JsonNode node = objectMapper.readTree(jsonStr);
            JsonNode choices = node.path("choices");
            if (choices.isArray() && !choices.isEmpty()) {
                JsonNode delta = choices.get(0).path("delta");
                JsonNode contentNode = delta.path("content");
                if (contentNode.isTextual()) {
                    return contentNode.asText();
                }
            }
        } catch (Exception e) {
            log.debug("JSON parse skip ({}): {}", jsonStr.length(), e.getMessage());
        }
        return null;
    }

    @Override
    public String getProviderName() {
        return "openai-compat";
    }

    @Override
    public boolean isAvailable() {
        return properties.getAi().getApiEndpoint() != null
                && !properties.getAi().getApiEndpoint().isBlank()
                && properties.getAi().getApiKey() != null
                && !properties.getAi().getApiKey().isBlank();
    }

    @Override
    public FoodRecognitionResult recognizeFood(String description) {
        if (!isAvailable()) {
            throw new IllegalStateException("AI API 未配置");
        }
        String endpoint = properties.getAi().getApiEndpoint();
        String model = properties.getAi().getModel();
        String apiKey = properties.getAi().getApiKey();

        log.info("[OpenAiCompat] 食物识别: {}", description);

        WebClient client = buildClient(endpoint, apiKey);
        Map<String, Object> requestBody = Map.of(
                "model", model,
                "messages", List.of(
                        Map.of("role", "system", "content", FOOD_RECOGNITION_PROMPT),
                        Map.of("role", "user", "content", "请分析以下食物描述的营养成分：" + description)
                ),
                "max_tokens", 300,
                "temperature", 0.1
        );

        try {
            String raw = client.post()
                    .uri("/chat/completions")
                    .bodyValue(requestBody)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block(Duration.ofSeconds(60));

            JsonNode root = objectMapper.readTree(raw);
            String content = root.path("choices").path(0).path("message").path("content").asText("");
            String json = extractJson(content);

            FoodRecognitionResult result = objectMapper.readValue(json, FoodRecognitionResult.class);
            log.info("[OpenAiCompat] 食物识别完成: {}", result.getFoodName());
            return result;
        } catch (AiApiException e) {
            throw e;
        } catch (Exception e) {
            log.error("[OpenAiCompat] 食物识别失败: {}", e.getMessage());
            throw new AiApiException("openai-compat", "食物识别失败，请稍后重试", e);
        }
    }

    @Override
    public String generateCoachFeedback(UserProfileRequest profile, String context) {
        if (!isAvailable()) throw new IllegalStateException("AI API 未配置");
        String ragCtx = ragService.buildInjectedContext(context);
        String userContent = "用户：" + p(profile) + "\n\n" + context
                + "\n" + ragCtx
                + "\n请作为 AI 营养教练，用中文给出今日与近期最要紧的 3-5 条行动建议，语气亲切、可执行。"
                + "如有权威营养数据请优先参考引用。";
        try {
            return chatText(COACH_SYSTEM_PROMPT, userContent, 700, 0.7);
        } catch (Exception e) {
            log.error("[OpenAiCompat] AI 教练生成失败: {}", e.getMessage());
            return "暂时无法生成教练建议，请稍后再试。";
        }
    }

    @Override
    public NutritionGapDto interpretGap(NutritionGapDto gap) {
        if (!isAvailable()) return gap;
        StringBuilder sb = new StringBuilder("用户近7日营养达标情况：\n");
        sb.append(String.format("- 热量 %.0f%% · 蛋白质 %.0f%% · 碳水 %.0f%% · 脂肪 %.0f%%\n",
                nz(gap.getCalPercent()), nz(gap.getProteinPercent()),
                nz(gap.getCarbsPercent()), nz(gap.getFatPercent())));
        for (NutritionGapDto.GapItem d : gap.getDeficiencies()) {
            sb.append(String.format("- %s%s，实际 %.0f%%\n", d.getNutrient(), d.getIssue(), d.getPercent()));
        }
        for (NutritionGapDto.GapItem s : gap.getSurpluses()) {
            sb.append(String.format("- %s%s，实际 %.0f%%\n", s.getNutrient(), s.getIssue(), s.getPercent()));
        }
        sb.append("请用中文解读这些缺口，给出 2-4 条可执行的调整建议。请参考中国居民膳食指南的权威建议。");
        String ragCtx = ragService.buildInjectedContext(sb.toString());
        if (!ragCtx.isBlank()) {
            sb.append("\n").append(ragCtx);
        }
        try {
            gap.setAiInsight(chatText(COACH_SYSTEM_PROMPT, sb.toString(), 700, 0.6));
        } catch (Exception e) {
            log.warn("[OpenAiCompat] 缺口解读失败: {}", e.getMessage());
            gap.setAiInsight("请结合以上达标数据，优先补充偏低项、控制偏高项。");
        }
        return gap;
    }

    @Override
    public RecommendationResponse recommendFromFridge(UserProfileRequest profile, List<String> ingredients, String context) {
        if (!isAvailable()) throw new IllegalStateException("AI API 未配置");
        String list = ingredients == null || ingredients.isEmpty() ? "（用户未指定，自行合理搭配）" : String.join("、", ingredients);
        String ctxText = context == null || context.isBlank() ? "" : "\n" + context;
        String userContent = String.format("""
                用户信息：%s

                家里现有的食材/原料：%s
                %s
                请仅使用以上食材，结合营养缺口，给出 2-3 个“今晚就能做”的菜，并为每道菜标注 1 句“为什么推荐给你”。
                严格按 JSON 返回，包含 summary / nutritionalAdvice / recipes / generalTips。
                """, p(profile), list, ctxText);
        String raw = chatRaw(FRIDGE_SYSTEM_PROMPT, userContent, 3000, 0.7);
        RecommendationResponse resp = parseResponse(raw);
        resp.setAiProvider(properties.getAi().getProvider());
        resp.setGeneratedAt(System.currentTimeMillis());
        return resp;
    }

    private String p(UserProfileRequest x) {
        if (x == null) return "未提供";
        return String.format("%s，%s岁，%.0fcm/%.0fkg，活动量%s，目标%s，口味%s，地区%s，忌讳%s",
                x.getGender() != null ? x.getGender() : "未知",
                x.getAge() != null ? x.getAge() : "未知",
                x.getHeightCm() != null ? x.getHeightCm() : 0.0,
                x.getWeightKg() != null ? x.getWeightKg() : 0.0,
                x.getActivityLevel() != null ? x.getActivityLevel() : "未知",
                x.getHealthGoal() != null ? x.getHealthGoal() : "未知",
                joinTastes(x.getTastePreferences()),
                x.getRegion() != null && !x.getRegion().isBlank() ? x.getRegion() : "未指定",
                joinList(x.getFoodAllergies()));
    }

    private String joinList(List<String> list) {
        return list == null || list.isEmpty() ? "无" : String.join("、", list);
    }

    private String joinTastes(List<UserProfileRequest.TastePreference> list) {
        return list == null || list.isEmpty() ? "未知" : String.join("、", list.stream().map(Enum::name).toList());
    }

    private double nz(Double d) { return d == null ? 0 : d; }

    private String chatText(String systemPrompt, String userContent, int maxTokens, double temperature) {
        String raw = chatRaw(systemPrompt, userContent, maxTokens, temperature);
        try {
            return objectMapper.readTree(raw).path("choices").path(0).path("message").path("content").asText("");
        } catch (Exception e) {
            log.warn("chatText JSON 解析失败: {}", e.getMessage());
            return raw;
        }
    }

    @Override
    public String chatWithContext(String systemPrompt, String userContent) {
        if (!isAvailable()) {
            throw new IllegalStateException("AI API 未配置");
        }
        String ragCtx = ragService.buildInjectedContext(userContent);
        String enrichedContent = userContent + (ragCtx.isBlank() ? "" : "\n\n" + ragCtx);
        return chatText(systemPrompt, enrichedContent, 800, 0.8);
    }

    @Override
    public Flux<String> chatStreamWithContext(String systemPrompt, String userContent) {
        if (!isAvailable()) {
            String fallback = "AI 助手暂未配置 API Key。请在启动时设置 AI_API_KEY 环境变量。不过简单指令仍然可用：记体重、记饮食、冰箱推荐、缺口查询。";
            return Flux.just(fallback);
        }

        String ragCtx = ragService.buildInjectedContext(userContent);
        String enrichedContent = userContent + (ragCtx.isBlank() ? "" : "\n\n" + ragCtx);

        String endpoint = properties.getAi().getApiEndpoint();
        String model = properties.getAi().getModel();
        String apiKey = properties.getAi().getApiKey();

        log.info("[OpenAiCompat] 流式对话: endpoint={}, model={}", endpoint, model);

        WebClient client = buildClient(endpoint, apiKey);

        Map<String, Object> requestBody = Map.of(
                "model", model,
                "stream", true,
                "messages", List.of(
                        Map.of("role", "system", "content", systemPrompt != null ? systemPrompt : ""),
                        Map.of("role", "user", "content", enrichedContent)
                ),
                "temperature", 0.75,
                "max_tokens", 1200
        );

        return client.post()
                .uri("/chat/completions")
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.TEXT_EVENT_STREAM)
                .bodyValue(requestBody)
                .retrieve()
                .bodyToFlux(String.class)
                .mapNotNull(this::extractContent)
                .doOnError(e -> log.error("[OpenAiCompat] 流式对话错误: {}", e.getMessage()))
                .doOnComplete(() -> log.info("[OpenAiCompat] 流式对话完成"));
    }

    private String chatRaw(String systemPrompt, String userContent, int maxTokens, double temperature) {
        WebClient client = buildClient(properties.getAi().getApiEndpoint(), properties.getAi().getApiKey());
        Map<String, Object> body = Map.of(
                "model", properties.getAi().getModel(),
                "messages", List.of(
                        Map.of("role", "system", "content", systemPrompt != null ? systemPrompt : ""),
                        Map.of("role", "user", "content", userContent)
                ),
                "temperature", temperature,
                "max_tokens", maxTokens
        );
        return client.post().uri("/chat/completions").bodyValue(body)
                .retrieve().bodyToMono(String.class).block(Duration.ofSeconds(120));
    }

    private static final String COACH_SYSTEM_PROMPT = """
            你是贴心且专业的 AI 营养教练。性格温和、语气亲切、实用主义。
            你非常清楚用户“今天已吃了什么、本想要摄入多少”的实时状态，因此你的建议永远是围绕“当前剩余配额”展开的：
            - 如果有用户刚吃的食物，先简短认可，再指出它对今天配额的影响
            - 给出今天接下来“吃什么、吃多少”的最优解，重点是即时可用
            - 如果某项营养素已经接近目标，提醒别超；如果还差很多，给出低成本补齐食物的建议
            只输出建议正文，不要废话，不要使用 Markdown 标题或代码块。
            """;

    private static final String FRIDGE_SYSTEM_PROMPT = """
            你是擅长“就地取材”的 AI 营养师。用户给出家里现有的食材，你必须：
            1. 只用这些食材（数量不足可搭配少量家中常备基础料：油、盐、葱姜蒜、酱油等），不要另外采购
            2. 结合用户近期营养缺口与健康目标，优先用现有食材补齐缺口
            3. 每道菜必须包含 1 句“为什么推荐给你”（贴合用户缺口/口味/健康目标）
            4. 严格返回以下 JSON（用 ```json``` 包裹，不要有多余逗号）：
            {
              "summary": "整体说明（1-2句，包含缺口回应的思路）",
              "nutritionalAdvice": { "dailyCalories": 数值, "proteinGrams": 数值, "carbsGrams": 数值, "fatGrams": 数值, "bmiCategory": "分类", "metabolismNote": "说明" },
              "recipes": [ { "name": "菜名", "description": "为什么推荐给你（1句，说明针对哪个缺口）", "ingredients": ["食材(含分量)"], "steps": ["步骤"], "tags": ["标签"], "estimatedTime": "用时", "difficulty": "简单/中等/困难", "nutritionalInfo": {"calories": 数值, "protein": 数值, "carbs": 数值, "fat": 数值} } ],
              "generalTips": ["3-5条建议"]
            }
            """;

    private static final String FOOD_RECOGNITION_PROMPT = """
            你是营养分析专家。用户会描述一种食物（含份量），你需要分析其营养成分并严格返回 JSON：
            {
              "foodName": "食物名称",
              "mealType": "餐次(breakfast/lunch/dinner/snack)",
              "calories": 热量数值(kcal),
              "proteinGrams": 蛋白质含量(g),
              "carbsGrams": 碳水含量(g),
              "fatGrams": 脂肪含量(g),
              "servingSize": 份量(g)
            }
            根据食物描述和常见营养数据估算，只返回 JSON，不要任何解释。
            """;

    private WebClient buildClient(String endpoint, String apiKey) {
        return webClientBuilder
                .baseUrl(endpoint)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                .build();
    }



    private static final String SYSTEM_PROMPT = """
            你是资深营养师AI助手，拥有丰富的临床营养学经验。你必须严格返回一个 JSON 对象（用 ```json``` 代码块包裹），包含以下字段：
            {
              "summary": "用户营养状况概要，包含对近期饮食数据的分析（2-3句话）",
              "nutritionalAdvice": {
                "dailyCalories": "每日热量建议数值(不带单位，纯数字)",
                "proteinGrams": 数值,
                "carbsGrams": 数值,
                "fatGrams": 数值,
                "bmiCategory": "BMI分类（偏瘦/正常/超重/肥胖）",
                "metabolismNote": "代谢及营养分析说明（1-2句话，结合用户近期饮食数据）"
              },
              "recipes": [
                {
                  "name": "菜名（尽量结合用户所在地区特色）",
                  "description": "为什么要推荐这道菜（1句话）",
                  "ingredients": ["食材1(含分量)", "食材2(含分量)"],
                  "steps": ["步骤1", "步骤2"],
                  "tags": ["标签"],
                  "estimatedTime": "预计用时",
                  "difficulty": "难度（简单/中等/困难）",
                  "nutritionalInfo": {"calories": 数值}
                }
              ],
              "generalTips": ["结合用户近期饮食数据给出的针对性建议1", "建议2", "建议3"]
            }

            核心原则：
            1. 如果提供了用户近期饮食数据，必须据此分析营养摄入是否达标/过量，并在建议中指明
            2. 优先推荐用户所在地域的特色时令食材和家常做法
            3. 充分考虑用户的健康目标（减脂/增肌/维持等）来调整热量和营养素配比
            4. 避开用户标注的食物过敏源和忌讳
            5. recipes 数组必须包含 2-4 个食谱，每个食谱的 ingredients 至少 3 项，steps 至少 2 步
            6. 严格返回合法 JSON，不要有多余的逗号
            """;

    private String buildPrompt(UserProfileRequest p, String foodDiaryContext) {
        String ragCtx = ragService.buildInjectedContext(buildRagQuery(p));
        return String.format("""
                请为以下用户生成个性化饮食建议：

                【用户信息】
                - 性别: %s
                - 年龄: %d
                - 身高: %.1f cm
                - 体重: %.1f kg
                - 活动量: %s
                - 健康目标: %s
                - 口味偏好: %s
                - 所在地区: %s
                - 喜欢的食物: %s
                - 食物忌讳/过敏: %s

                推荐食谱时请优先融入该地区特色食材和烹饪风格，让建议更贴近用户的日常饮食习惯。
                %s
                %s
                请严格按 JSON 格式返回，包含 summary / nutritionalAdvice / recipes(2-4个) / generalTips(3-5条)。
                """,
                p.getGender(), p.getAge() != null ? p.getAge() : 30,
                p.getHeightCm() != null ? p.getHeightCm() : 170.0,
                p.getWeightKg() != null ? p.getWeightKg() : 65.0,
                p.getActivityLevel(), p.getHealthGoal(), joinTastes(p.getTastePreferences()),
                p.getRegion() != null && !p.getRegion().isBlank() ? p.getRegion() : "未指定",
                joinList(p.getFoodPreferences()), joinList(p.getFoodAllergies()),
                foodDiaryContext != null ? foodDiaryContext : "",
                ragCtx != null && !ragCtx.isBlank() ? ragCtx : ""
        );
    }

    private String buildStreamPrompt(UserProfileRequest p, String foodDiaryContext) {
        String ragCtx = ragService.buildInjectedContext(buildRagQuery(p));
        return String.format("""
                用户资料：%s，%d岁，%.0fcm / %.0fkg，活动量%s，目标%s，喜欢%s口味，所在地区%s，偏好食物%s，忌讳%s。
                %s
                %s
                请给这位用户一份完整的饮食建议，**严格按以下 Markdown 结构输出**：

                ## 用户健康评估
                （计算BMI、评估营养状况、给出基础代谢参考值）

                ## 近期饮食分析
                （如果提供了饮食数据，请分析达标率是否合理、存在什么问题；如果没有数据则简要说明需要记录饮食）

                ## 推荐食谱（结合用户所在地区的特色食材和常见做法）
                ### 食谱一：菜名
                - 食材：...（含分量）
                - 做法：...
                - 热量：...kcal 蛋白质：...g 碳水：...g 脂肪：...g

                ### 食谱二：菜名
                - 食材：...（含分量）
                - 做法：...
                - 热量：...kcal 蛋白质：...g 碳水：...g 脂肪：...g

                ## 个性化饮食建议
                - 建议1（结合饮食数据和健康目标）
                - 建议2
                - 建议3

                请用中文自然回答，语气亲切，每个部分要有足够细节。必须用 ## / ### / - 这些 Markdown 标记。
                """,
                "MALE".equalsIgnoreCase(String.valueOf(p.getGender())) ? "男性" : "女性",
                p.getAge() != null ? p.getAge() : 30,
                p.getHeightCm() != null ? p.getHeightCm() : 170.0,
                p.getWeightKg() != null ? p.getWeightKg() : 65.0,
                p.getActivityLevel(), p.getHealthGoal(), joinTastes(p.getTastePreferences()),
                p.getRegion() != null && !p.getRegion().isBlank() ? p.getRegion() : "未指定",
                joinList(p.getFoodPreferences()), joinList(p.getFoodAllergies()),
                foodDiaryContext != null ? foodDiaryContext : "",
                ragCtx != null && !ragCtx.isBlank() ? ragCtx : ""
        );
    }

    /** 依据用户画像构造 RAG 检索词，把目标/地区/口味/偏好食物拼成查询 */
    private String buildRagQuery(UserProfileRequest p) {
        StringBuilder q = new StringBuilder();
        if (p.getHealthGoal() != null) q.append(p.getHealthGoal().name()).append(" ");
        if (p.getRegion() != null && !p.getRegion().isBlank()) q.append(p.getRegion()).append(" ");
        q.append(joinTastes(p.getTastePreferences())).append(" ");
        q.append(joinList(p.getFoodPreferences()));
        return q.toString().trim();
    }

    private RecommendationResponse parseResponse(String raw) {
        try {
            JsonNode root = objectMapper.readTree(raw);
            String content = root.path("choices").path(0).path("message").path("content").asText("");

            String json = extractJson(content);
            log.info("[OpenAiCompat] 提取到 JSON 长度={}, 内容={}", json.length(), json);

            RecommendationResponse resp = objectMapper.readValue(json, RecommendationResponse.class);
            log.info("[OpenAiCompat] 解析成功 recipes={} tips={}", resp.getRecipes() == null ? 0 : resp.getRecipes().size(),
                    resp.getGeneralTips() == null ? 0 : resp.getGeneralTips().size());
            if (resp.getRecipes() == null) resp.setRecipes(List.of());
            if (resp.getGeneralTips() == null) resp.setGeneralTips(List.of());
            return resp;
        } catch (Exception e) {
            log.error("[OpenAiCompat] JSON 解析失败: {}", e.getMessage(), e);
            try {
                JsonNode root = objectMapper.readTree(raw);
                String content = root.path("choices").path(0).path("message").path("content").asText("");
                RecommendationResponse fallback = new RecommendationResponse();
                fallback.setSummary(content);
                fallback.setRecipes(List.of());
                fallback.setGeneralTips(List.of());
                return fallback;
            } catch (Exception e2) {
                log.error("[OpenAiCompat] 二次 fallback 也失败: {}", e2.getMessage());
                RecommendationResponse fallback = new RecommendationResponse();
                fallback.setSummary("AI 暂时无法生成推荐，请稍后再试。");
                fallback.setRecipes(List.of());
                fallback.setGeneralTips(List.of());
                return fallback;
            }
        }
    }

    private String extractJson(String content) {
        String cleaned = content;
        Matcher fenced = Pattern.compile("```(?:json)?\\s*", Pattern.CASE_INSENSITIVE).matcher(content);
        if (fenced.find()) {
            int start = fenced.end();
            int end = content.indexOf("```", start);
            if (end > start) cleaned = content.substring(start, end);
            else cleaned = content.substring(start);
        }

        int firstBrace = cleaned.indexOf('{');
        if (firstBrace < 0) return cleaned;

        int depth = 0;
        boolean inString = false;
        char prev = 0;
        for (int i = firstBrace; i < cleaned.length(); i++) {
            char c = cleaned.charAt(i);
            if (prev != '\\') {
                if (c == '"') inString = !inString;
            }
            if (!inString) {
                if (c == '{') depth++;
                else if (c == '}') {
                    depth--;
                    if (depth == 0) {
                        String json = cleaned.substring(firstBrace, i + 1);
                        return removeTrailingCommas(json);
                    }
                }
            }
            prev = c;
        }
        return removeTrailingCommas(cleaned.substring(firstBrace));
    }

    private String removeTrailingCommas(String json) {
        return json.replaceAll(",\\s*([}\\]])", "$1");
    }
}