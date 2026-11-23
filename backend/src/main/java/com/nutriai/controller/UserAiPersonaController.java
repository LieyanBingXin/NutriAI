package com.nutriai.controller;

import com.nutriai.dto.ApiResponse;
import com.nutriai.entity.UserAiPersona;
import com.nutriai.service.AuthService;
import com.nutriai.service.UserAiPersonaService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/ai-persona")
@RequiredArgsConstructor
public class UserAiPersonaController {

    private final UserAiPersonaService service;
    private final AuthService authService;

    private Long requireUserId() {
        Long userId = authService.getCurrentUserId();
        if (userId == null) throw new com.nutriai.exception.AiApiException(401, "请先登录");
        return userId;
    }

    @GetMapping
    public ApiResponse<List<String>> list() {
        Long userId = requireUserId();
        return ApiResponse.success(service.listTags(userId));
    }

    @PostMapping
    public ApiResponse<UserAiPersona> add(@RequestBody Map<String, String> body) {
        Long userId = requireUserId();
        String tagName = body.get("tagName");
        if (tagName == null || tagName.isBlank()) {
            throw new com.nutriai.exception.AiApiException(400, "标签不能为空");
        }
        return ApiResponse.success(service.addTag(userId, tagName));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> remove(@PathVariable Long id) {
        Long userId = requireUserId();
        service.removeTag(userId, id);
        return ApiResponse.success(null);
    }

    @DeleteMapping("/by-name/{tagName}")
    public ApiResponse<Void> removeByName(@PathVariable String tagName) {
        Long userId = requireUserId();
        service.removeTagByName(userId, tagName);
        return ApiResponse.success(null);
    }

    @DeleteMapping
    public ApiResponse<Void> clearAll() {
        Long userId = requireUserId();
        service.clearAll(userId);
        return ApiResponse.success(null);
    }
}