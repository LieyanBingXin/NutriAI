package com.nutriai.service;

import com.nutriai.dto.FoodDiaryRequest;
import com.nutriai.dto.RecipeBookDto;
import com.nutriai.entity.RecipeBook;
import com.nutriai.entity.User;
import com.nutriai.exception.AiApiException;
import com.nutriai.repository.RecipeBookRepository;
import com.nutriai.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class RecipeBookService {

    private final RecipeBookRepository recipeBookRepository;
    private final UserRepository userRepository;
    private final FoodDiaryService foodDiaryService;

    @Transactional
    public RecipeBookDto save(Long userId, RecipeBookDto dto) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new AiApiException(404, "用户不存在"));

        RecipeBook recipe = new RecipeBook();
        recipe.setUser(user);
        recipe.setName(dto.getName().trim());
        recipe.setDescription(dto.getDescription());
        recipe.setMealType(normalizeMealType(dto.getMealType()));
        recipe.setIngredientsText(dto.getIngredientsText());
        recipe.setStepsText(dto.getStepsText());
        recipe.setCalories(dto.getCalories());
        recipe.setProteinGrams(dto.getProteinGrams());
        recipe.setCarbsGrams(dto.getCarbsGrams());
        recipe.setFatGrams(dto.getFatGrams());
        recipe.setTags(dto.getTags());

        recipe = recipeBookRepository.save(recipe);
        log.info("[食谱本] 已保存: userId={}, name={}", userId, recipe.getName());
        return RecipeBookDto.fromEntity(recipe);
    }

    private String normalizeMealType(String raw) {
        if (raw == null || raw.isBlank()) return "lunch";
        String s = raw.trim().toLowerCase();
        if (s.contains("早") || s.contains("breakfast")) return "breakfast";
        if (s.contains("午") || s.contains("lunch")) return "lunch";
        if (s.contains("晚") || s.contains("dinner")) return "dinner";
        if (s.contains("加餐") || s.contains("snack")) return "snack";
        return "lunch";
    }

    public Page<RecipeBookDto> list(Long userId, String mealType, Pageable pageable) {
        Page<RecipeBook> page;
        if (mealType != null && !mealType.isBlank()) {
            page = recipeBookRepository.findByUserIdAndMealTypeOrderByCreatedAtDesc(
                    userId, normalizeMealType(mealType), pageable);
        } else {
            page = recipeBookRepository.findByUserIdOrderByCreatedAtDesc(userId, pageable);
        }
        return page.map(RecipeBookDto::fromEntity);
    }

    @Transactional
    public void delete(Long userId, Long recipeId) {
        RecipeBook recipe = recipeBookRepository.findById(recipeId)
                .orElseThrow(() -> new AiApiException(404, "食谱不存在"));
        if (!recipe.getUser().getId().equals(userId)) {
            throw new AiApiException(403, "无权操作该食谱");
        }
        recipeBookRepository.delete(recipe);
        log.info("[食谱本] 已删除: userId={}, recipeId={}", userId, recipeId);
    }

    @Transactional
    public FoodDiaryRequest toFoodDiaryRequest(Long userId, Long recipeId) {
        RecipeBook recipe = recipeBookRepository.findById(recipeId)
                .orElseThrow(() -> new AiApiException(404, "食谱不存在"));
        if (!recipe.getUser().getId().equals(userId)) {
            throw new AiApiException(403, "无权操作该食谱");
        }

        FoodDiaryRequest req = new FoodDiaryRequest();
        req.setFoodName(recipe.getName());
        req.setMealType(recipe.getMealType() != null ? recipe.getMealType() : "lunch");
        req.setAmountGram(100.0);
        req.setCalories(recipe.getCalories() != null ? recipe.getCalories() : 0.0);
        req.setProtein(recipe.getProteinGrams());
        req.setCarbs(recipe.getCarbsGrams());
        req.setFat(recipe.getFatGrams());
        req.setNote("来自食谱本：" + recipe.getName());
        return req;
    }
}