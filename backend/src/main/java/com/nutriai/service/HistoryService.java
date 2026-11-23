package com.nutriai.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nutriai.dto.HistoryDetailDto;
import com.nutriai.dto.HistoryRecordDto;
import com.nutriai.dto.RecommendationResponse;
import com.nutriai.dto.UserProfileRequest;
import com.nutriai.entity.RecommendationRecord;
import com.nutriai.exception.AiApiException;
import com.nutriai.repository.RecommendationRecordRepository;
import com.nutriai.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class HistoryService {

    private final RecommendationRecordRepository recordRepository;
    private final UserRepository userRepository;
    private final ObjectMapper objectMapper;

    @Transactional
    public RecommendationRecord saveRecord(Long userId, UserProfileRequest profile, RecommendationResponse response, String aiProvider) {
        RecommendationRecord record = new RecommendationRecord();
        record.setUser(userRepository.findById(userId).orElseThrow());
        try {
            record.setProfileJson(objectMapper.writeValueAsString(profile));
            record.setResponseJson(objectMapper.writeValueAsString(response));
        } catch (JsonProcessingException e) {
            log.error("序列化推荐记录失败", e);
            throw new AiApiException("保存历史记录失败: " + e.getMessage());
        }
        record.setAiProvider(aiProvider);
        return recordRepository.save(record);
    }

    public Page<HistoryRecordDto> listRecords(Long userId, int page, int size, String keyword) {
        Pageable pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 50),
                Sort.by(Sort.Direction.DESC, "createdAt"));
        String q = keyword == null ? "" : keyword.trim();
        Page<RecommendationRecord> result = q.isEmpty()
                ? recordRepository.findByUserIdOrderByCreatedAtDesc(userId, pageable)
                : recordRepository.searchByUserId(userId, q, pageable);
        return result.map(this::toSummaryDto);
    }

    public HistoryDetailDto getDetail(Long userId, Long recordId) {
        RecommendationRecord record = recordRepository.findById(recordId)
                .orElseThrow(() -> new AiApiException(404, "记录不存在"));

        if (!record.getUser().getId().equals(userId)) {
            throw new AiApiException(403, "无权访问该记录");
        }

        try {
            UserProfileRequest profile = objectMapper.readValue(record.getProfileJson(), UserProfileRequest.class);
            RecommendationResponse recommendation = objectMapper.readValue(record.getResponseJson(), RecommendationResponse.class);

            return new HistoryDetailDto(
                    record.getId(),
                    profile,
                    recommendation,
                    record.getAiProvider(),
                    record.getCreatedAt()
            );
        } catch (JsonProcessingException e) {
            throw new AiApiException("解析历史记录失败: " + e.getMessage());
        }
    }

    @Transactional
    public void delete(Long userId, Long recordId) {
        RecommendationRecord record = recordRepository.findById(recordId)
                .orElseThrow(() -> new AiApiException(404, "记录不存在"));
        if (!record.getUser().getId().equals(userId)) {
            throw new AiApiException(403, "无权删除该记录");
        }
        recordRepository.delete(record);
    }

    private HistoryRecordDto toSummaryDto(RecommendationRecord record) {
        String summary = null;
        int recipesCount = 0;
        try {
            JsonNode root = objectMapper.readTree(record.getResponseJson());
            summary = root.path("summary").asText(null);
            JsonNode recipes = root.path("recipes");
            if (recipes.isArray()) {
                recipesCount = recipes.size();
            }
        } catch (Exception ignored) {
        }
        return new HistoryRecordDto(record.getId(), record.getAiProvider(), summary, recipesCount, record.getCreatedAt());
    }
}