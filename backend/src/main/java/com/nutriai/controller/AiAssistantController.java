package com.nutriai.controller;

import com.nutriai.dto.ApiResponse;
import com.nutriai.dto.AssistantChatRequest;
import com.nutriai.dto.AssistantChatResponse;
import com.nutriai.dto.NutritionGapDto;
import com.nutriai.entity.FoodPreference;
import com.nutriai.service.*;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Map;

/**
 * AI 对话助手 —— 自然语言 → 执行推荐/记日记/记体重/记偏好/冰箱记忆/缺口分析
 */
@RestController
@RequestMapping("/api/assistant")
@RequiredArgsConstructor
public class AiAssistantController {

    private final AssistantIntentService assistantIntentService;
    private final FridgeService fridgeService;
    private final FoodPreferenceService foodPreferenceService;
    private final AiCoachService aiCoachService;
    private final AuthService authService;
    private final UserProfileService userProfileService;
    private final UserAiPersonaService userAiPersonaService;
    private final RailStateService railStateService;

    @PostMapping("/chat")
    public ApiResponse<AssistantChatResponse> chat(@Valid @RequestBody AssistantChatRequest req) {
        Long userId = authService.getCurrentUserId();
        AssistantChatResponse r = assistantIntentService.chat(userId, req.getMessage(), req.getEmphasizeTags(), req.getHistory(), req.getSuggestedRecipes());
        return ApiResponse.success(r);
    }

    /** 流式对话 —— SSE 逐 token 输出，意图匹配命中的话一次性返回 */
    @PostMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> chatStream(@Valid @RequestBody AssistantChatRequest req) {
        Long userId = authService.getCurrentUserId();
        return assistantIntentService.chatStream(userId, req.getMessage(), req.getEmphasizeTags(), req.getHistory(), req.getSuggestedRecipes());
    }

    /** 便利贴看板：一次性返回喜好/冰箱/缺口三大面板需要的数据 */
    @GetMapping("/board")
    public ApiResponse<Map<String, Object>> board() {
        Long userId = authService.getCurrentUserId();
        Map<String, Object> data = new java.util.LinkedHashMap<>();
        data.put("fridge", fridgeService.listItems(userId));
        List<FoodPreference> prefs = foodPreferenceService.getUserPreferences(userId);
        data.put("preferences", prefs);

        com.nutriai.dto.UserProfileRequest profile = buildProfile(userId);
        NutritionGapDto gap = aiCoachService.getNutritionGap(userId, profile);
        data.put("gap", gap);
        data.put("personaTags", userAiPersonaService.listTags(userId));
        data.put("profile", userProfileService.getProfile(userId));
        return ApiResponse.success(data);
    }

    /** 主动建议：打开助手页时按今日记录给一句确定性建议 */
    @GetMapping("/proactive-tip")
    public ApiResponse<Map<String, Object>> proactiveTip() {
        Long userId = authService.getCurrentUserId();
        return ApiResponse.success(assistantIntentService.proactiveTip(userId));
    }

    private com.nutriai.dto.UserProfileRequest buildProfile(Long userId) {
        com.nutriai.dto.UserProfileRequest p = new com.nutriai.dto.UserProfileRequest();
        p.setGender(com.nutriai.dto.UserProfileRequest.Gender.MALE);
        p.setAge(30); p.setHeightCm(170.0); p.setWeightKg(65.0);
        p.setActivityLevel(com.nutriai.dto.UserProfileRequest.ActivityLevel.MODERATE);
        p.setHealthGoal(com.nutriai.dto.UserProfileRequest.HealthGoal.MAINTAIN);
        p.setTastePreferences(java.util.List.of(com.nutriai.dto.UserProfileRequest.TastePreference.LIGHT));
        try {
            com.nutriai.dto.UserProfileDto existing = userProfileService.getProfile(userId);
            if (existing != null) {
                if (existing.getAge() != null) p.setAge(existing.getAge());
                if (existing.getHeightCm() != null) p.setHeightCm(existing.getHeightCm());
                if (existing.getWeightKg() != null) p.setWeightKg(existing.getWeightKg());
                if (existing.getGender() != null) {
                    try { p.setGender(com.nutriai.dto.UserProfileRequest.Gender.valueOf(existing.getGender().toUpperCase())); } catch (Exception ignored) {}
                }
                if (existing.getActivityLevel() != null) {
                    try { p.setActivityLevel(com.nutriai.dto.UserProfileRequest.ActivityLevel.valueOf(existing.getActivityLevel().toUpperCase())); } catch (Exception ignored) {}
                }
                if (existing.getHealthGoal() != null) {
                    try { p.setHealthGoal(com.nutriai.dto.UserProfileRequest.HealthGoal.valueOf(existing.getHealthGoal().toUpperCase())); } catch (Exception ignored) {}
                }
                if (existing.getTastePreferences() != null && !existing.getTastePreferences().isEmpty()) {
                    p.setTastePreferences(existing.getTastePreferences().stream()
                            .map(s -> { try { return com.nutriai.dto.UserProfileRequest.TastePreference.valueOf(s.toUpperCase()); } catch (Exception e) { return null; } })
                            .filter(java.util.Objects::nonNull)
                            .toList());
                }
                if (existing.getRegion() != null) p.setRegion(existing.getRegion());
            }
        } catch (Exception ignored) {}
        return p;
    }

    /** 冰箱增删便利贴动作 */
    @PostMapping("/fridge/remember")
    public ApiResponse<Integer> fridgeRemember(@RequestBody Map<String, Object> body) {
        Long userId = authService.getCurrentUserId();
        if (userId == null) {
            throw new com.nutriai.exception.AiApiException(401, "请先登录");
        }
        Object raw = body.get("items");
        if (raw == null) raw = body.get("ingredients");
        List<String> items = new java.util.ArrayList<>();
        if (raw instanceof List<?> list) {
            for (Object o : list) {
                if (o != null && !o.toString().isBlank()) items.add(o.toString());
            }
        }
        if (items.isEmpty()) {
            throw new com.nutriai.exception.AiApiException(400, "食材列表不能为空");
        }
        return ApiResponse.success(fridgeService.remember(userId, items));
    }

    @PostMapping("/fridge/forget")
    public ApiResponse<String> fridgeForget(@RequestBody Map<String, String> body) {
        Long userId = authService.getCurrentUserId();
        if (userId == null) {
            throw new com.nutriai.exception.AiApiException(401, "请先登录");
        }
        if ("all".equals(body.get("mode"))) {
            fridgeService.forgetAll(userId);
        } else {
            fridgeService.forgetOne(userId, body.getOrDefault("ingredient", ""));
        }
        return ApiResponse.success("ok");
    }

    /** 便利贴 rail 读取 —— 跨设备同步 */
    @GetMapping("/rail")
    public ApiResponse<Map<String, Object>> getRail() {
        Long userId = authService.getCurrentUserId();
        if (userId == null) {
            throw new com.nutriai.exception.AiApiException(401, "请先登录");
        }
        return ApiResponse.success(railStateService.load(userId));
    }

    /** 便利贴 rail 整体保存 —— 跨设备同步 */
    @PutMapping("/rail")
    public ApiResponse<String> saveRail(@RequestBody Map<String, Object> body) {
        Long userId = authService.getCurrentUserId();
        if (userId == null) {
            throw new com.nutriai.exception.AiApiException(401, "请先登录");
        }
        railStateService.save(userId, body);
        return ApiResponse.success("ok");
    }
}