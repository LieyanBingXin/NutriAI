package com.nutriai.controller;

import com.nutriai.dto.ApiResponse;
import com.nutriai.dto.FoodDiaryDto;
import com.nutriai.dto.FoodDiaryRequest;
import com.nutriai.dto.RecipeBookDto;
import com.nutriai.exception.AiApiException;
import com.nutriai.service.AuthService;
import com.nutriai.service.FoodDiaryService;
import com.nutriai.service.RecipeBookService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/recipe-book")
@RequiredArgsConstructor
public class RecipeBookController {

    private final RecipeBookService recipeBookService;
    private final AuthService authService;
    private final FoodDiaryService foodDiaryService;

    @PostMapping
    public ApiResponse<RecipeBookDto> save(@Valid @RequestBody RecipeBookDto dto) {
        Long userId = requireUserId();
        return ApiResponse.success(recipeBookService.save(userId, dto));
    }

    @GetMapping
    public ApiResponse<Page<RecipeBookDto>> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String mealType) {
        Long userId = requireUserId();
        return ApiResponse.success(recipeBookService.list(userId, mealType, PageRequest.of(page, size)));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<String> delete(@PathVariable Long id) {
        Long userId = requireUserId();
        recipeBookService.delete(userId, id);
        return ApiResponse.success("Deleted");
    }

    @PostMapping("/{id}/use")
    public ApiResponse<FoodDiaryDto> useRecipe(@PathVariable Long id) {
        Long userId = requireUserId();
        FoodDiaryRequest req = recipeBookService.toFoodDiaryRequest(userId, id);
        FoodDiaryDto diary = foodDiaryService.addRecord(userId, req);
        return ApiResponse.success(diary);
    }

    private Long requireUserId() {
        Long userId = authService.getCurrentUserId();
        if (userId == null) {
            throw new AiApiException(401, "Not logged in");
        }
        return userId;
    }
}