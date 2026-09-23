package com.nutriai.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class HistoryDetailDto {

    private Long id;
    private UserProfileRequest profile;
    private RecommendationResponse recommendation;
    private String aiProvider;
    private LocalDateTime createdAt;
}