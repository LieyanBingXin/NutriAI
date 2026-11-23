package com.nutriai.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class WeightStatsDto {

    private int totalRecords;
    private Double firstWeight;
    private String firstDate;
    private Double currentWeight;
    private String currentDate;
    private Double changeKg;
    private Double changePercent;
    private Double avgWeight;
    private Double minWeight;
    private Double maxWeight;
}