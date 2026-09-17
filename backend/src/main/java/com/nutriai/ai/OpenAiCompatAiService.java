package com.nutriai.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nutriai.config.NutriAiProperties;
import com.nutriai.dto.RecommendationResponse;
import com.nutriai.dto.UserProfileRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 通用 AI 服务实现，通过 WebClient 调用外部大模型 API。
 * 支持 OpenAI 兼容格式的接口（如 DeepSeek、通义千问、本地 Ollama 等）。
 *
 * <p>后续可按需扩展：
 * <ul>
 *   <li>不同 provider 的请求/响应适配（如百度文心一格、讯飞星火等）</li>
 *   <li>流式响应（SSE）</li>
 *   <li>Prompt 模板化管理</li>
 *   <li>Token 统计与成本控制</li>
 * </ul>
 */
@Slf4j
@Component("openAiCompatAiService")
@RequiredArgsConstructor
public class OpenAiCompatAiService implements AiService {

    private final NutriAiProperties properties;
    private final WebClient.Builder webClientBuilder;
    private final ObjectMapper objectMapper;

    @Override
    public RecommendationResponse analyzeAndRecommend(UserProfileRequest profile) {
        if (!isAvailable()) {
            throw new IllegalStateException("AI API 未配置: " + properties.getAi().getProvider());
        }

        String endpoint = properties.getAi().getApiEndpoint();
        String model = properties.getAi().getModel();
        String apiKey = properties.getAi().getApiKey();

        log.info("[OpenAiCompat] 调用 AI: endpoint={}, model={}", endpoint, model);

        WebClient client = webClientBuilder
                .baseUrl(endpoint)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                .build();

        String prompt = buildPrompt(profile);

        Map<String, Object> requestBody = Map.of(
                "model", model,
                "messages", List.of(
                        Map.of("role", "system", "content", "你是一位专业的营养师AI助手。请根据用户信息给出饮食建议，返回 JSON 格式。"),
                        Map.of("role", "user", "content", prompt)
                ),
                "temperature", 0.7
        );

        // 注意：这里只写了同步调用的骨架，实际需要把响应解析成 RecommendationResponse
        // 为避免阻塞，生产环境建议用 Mono.block() 或改为非异步
        String rawResponse = client.post()
                .uri("/chat/completions")
                .bodyValue(requestBody)
                .retrieve()
                .bodyToMono(String.class)
                .block();

        log.debug("[OpenAiCompat] 原始响应: {}", rawResponse);

        RecommendationResponse response = parseResponse(rawResponse);
        response.setAiProvider(properties.getAi().getProvider());
        response.setGeneratedAt(System.currentTimeMillis());

        return response;
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

    private String buildPrompt(UserProfileRequest p) {
        return String.format("""
                用户信息：
                - 性别: %s
                - 年龄: %d
                - 身高: %.1f cm
                - 体重: %.1f kg
                - 活动量: %s
                - 健康目标: %s
                - 食物喜好: %s
                - 食物忌讳/过敏: %s
                - 口味偏好: %s
                
                请给出：
                1. 每日建议热量与宏量营养素分配
                2. 3道食谱（含食材、步骤、营养信息）
                3. 通用饮食建议
                
                请以 JSON 格式返回，结构参考：
                {
                  "summary": "...",
                  "nutritionalAdvice": {"dailyCalories": 0, "proteinGrams": 0, "carbsGrams": 0, "fatGrams": 0, "bmiCategory": "...", "metabolismNote": "..."},
                  "recipes": [{"name": "...", "description": "...", "ingredients": [], "steps": [], "nutritionalInfo": {...}, "estimatedTime": "...", "difficulty": "...", "tags": []}],
                  "generalTips": []
                }
                """,
                p.getGender(), p.getAge(), p.getHeightCm(), p.getWeightKg(),
                p.getActivityLevel(), p.getHealthGoal(),
                p.getFoodPreferences(), p.getFoodAllergies(), p.getTastePreference()
        );
    }

    private RecommendationResponse parseResponse(String raw) {
        try {
            JsonNode root = objectMapper.readTree(raw);
            String content = root.path("choices").path(0).path("message").path("content").asText("");

            String json = extractJson(content);
            log.info("[OpenAiCompat] 提取到的 JSON: {}", json);

            RecommendationResponse resp = objectMapper.readValue(json, RecommendationResponse.class);
            if (resp.getRecipes() == null) resp.setRecipes(List.of());
            if (resp.getGeneralTips() == null) resp.setGeneralTips(List.of());
            return resp;
        } catch (Exception e) {
            log.error("[OpenAiCompat] AI 响应解析失败: {}", e.getMessage(), e);
            RecommendationResponse fallback = new RecommendationResponse();
            fallback.setSummary("AI 响应解析失败，请稍后重试。");
            fallback.setRecipes(List.of());
            fallback.setGeneralTips(List.of());
            return fallback;
        }
    }

    private String extractJson(String content) {
        Matcher fenced = Pattern.compile("```(?:json)?\\s*(\\{[\\s\\S]*?\\})\\s*```", Pattern.CASE_INSENSITIVE).matcher(content);
        if (fenced.find()) return fenced.group(1);

        Matcher bare = Pattern.compile("\\{[\\s\\S]*\\}").matcher(content);
        if (bare.find()) return bare.group(0);

        return content;
    }
}