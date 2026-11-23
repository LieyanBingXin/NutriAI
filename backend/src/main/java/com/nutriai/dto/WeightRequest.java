package com.nutriai.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDate;

@Data
public class WeightRequest {

    @NotNull(message = "体重不能为空")
    @DecimalMin(value = "1.0", message = "体重必须大于 0")
    private Double weightKg;

    @PastOrPresent(message = "不能记录未来日期的体重")
    private LocalDate recordDate;

    @Size(max = 200, message = "备注过长")
    private String note;
}