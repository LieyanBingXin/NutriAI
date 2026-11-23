package com.nutriai.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class HistoryRecordDto {

    private Long id;
    private String aiProvider;
    private String summary;
    private int recipesCount;
    private LocalDateTime createdAt;
}