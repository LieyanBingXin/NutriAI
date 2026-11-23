package com.nutriai.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nutriai.entity.RailState;
import com.nutriai.entity.User;
import com.nutriai.exception.AiApiException;
import com.nutriai.repository.RailStateRepository;
import com.nutriai.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 便利贴 rail 持久化 —— 每个用户一条 JSON 快照，整体读写。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RailStateService {

    private final RailStateRepository railStateRepository;
    private final UserRepository userRepository;
    private final ObjectMapper objectMapper;

    private static final List<String> KEYS = List.of("likes", "dislikes", "fridge", "gap", "custom");

    private Map<String, Object> empty() {
        Map<String, Object> m = new LinkedHashMap<>();
        for (String k : KEYS) m.put(k, List.of());
        return m;
    }

    public Map<String, Object> load(Long userId) {
        String raw = railStateRepository.findByUserId(userId).map(RailState::getData).orElse(null);
        if (raw == null || raw.isBlank()) return empty();
        try {
            Map<String, Object> m = objectMapper.readValue(raw, new TypeReference<LinkedHashMap<String, Object>>() {});
            return normalize(m);
        } catch (Exception e) {
            log.warn("便利贴数据解析失败，回退为空: {}", e.getMessage());
            return empty();
        }
    }

    @Transactional
    public void save(Long userId, Map<String, Object> rail) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new AiApiException(404, "用户不存在"));
        Map<String, Object> normalized = normalize(rail);
        try {
            String json = objectMapper.writeValueAsString(normalized);
            RailState state = railStateRepository.findByUserId(userId).orElse(null);
            if (state == null) {
                state = new RailState(user, json);
            } else {
                state.setData(json);
            }
            railStateRepository.save(state);
        } catch (Exception e) {
            log.error("便利贴保存失败", e);
            throw new AiApiException(500, "便利贴保存失败");
        }
    }

    /** 只保留五个合法键，值必须是数组 */
    private Map<String, Object> normalize(Map<String, Object> rail) {
        Map<String, Object> m = new LinkedHashMap<>();
        for (String k : KEYS) {
            Object v = rail == null ? null : rail.get(k);
            m.put(k, v instanceof List<?> ? v : List.of());
        }
        return m;
    }
}