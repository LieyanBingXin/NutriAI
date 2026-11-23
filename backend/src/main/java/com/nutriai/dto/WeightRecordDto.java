package com.nutriai.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class WeightRecordDto {

    private Long id;
    private Double weightKg;
    private LocalDate recordDate;
    private String note;
    private LocalDateTime createdAt;

    public static WeightRecordDto from(com.nutriai.entity.WeightRecord wr) {
        return new WeightRecordDto(
                wr.getId(),
                wr.getWeightKg(),
                wr.getRecordDate(),
                wr.getNote(),
                wr.getCreatedAt()
        );
    }
}