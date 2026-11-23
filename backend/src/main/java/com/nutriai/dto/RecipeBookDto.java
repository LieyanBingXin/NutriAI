package com.nutriai.dto;

import com.nutriai.entity.RecipeBook;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class RecipeBookDto {

    private Long id;

    @NotBlank(message = "食谱名称不能为空")
    @Size(max = 200, message = "食谱名称过长")
    private String name;

    @Size(max = 500, message = "描述过长")
    private String description;

    private String mealType;
    private String ingredientsText;
    private String stepsText;
    private Double calories;
    private Double proteinGrams;
    private Double carbsGrams;
    private Double fatGrams;
    private String tags;
    private LocalDateTime createdAt;

    public static RecipeBookDto fromEntity(RecipeBook r) {
        RecipeBookDto dto = new RecipeBookDto();
        dto.setId(r.getId());
        dto.setName(r.getName());
        dto.setDescription(r.getDescription());
        dto.setMealType(r.getMealType());
        dto.setIngredientsText(r.getIngredientsText());
        dto.setStepsText(r.getStepsText());
        dto.setCalories(r.getCalories());
        dto.setProteinGrams(r.getProteinGrams());
        dto.setCarbsGrams(r.getCarbsGrams());
        dto.setFatGrams(r.getFatGrams());
        dto.setTags(r.getTags());
        dto.setCreatedAt(r.getCreatedAt());
        return dto;
    }
}