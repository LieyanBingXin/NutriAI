package com.nutriai.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserSummaryDto {

    private Long id;
    private String username;
    private String email;
    private String displayName;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}