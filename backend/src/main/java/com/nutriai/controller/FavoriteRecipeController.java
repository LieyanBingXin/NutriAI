package com.nutriai.controller;

import com.nutriai.dto.ApiResponse;
import com.nutriai.entity.FavoriteRecipe;
import com.nutriai.exception.AiApiException;
import com.nutriai.service.AuthService;
import com.nutriai.service.FavoriteRecipeService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/favorite-recipes")
@RequiredArgsConstructor
public class FavoriteRecipeController {

    private final FavoriteRecipeService service;
    private final AuthService authService;

    @GetMapping
    public ApiResponse<Page<FavoriteRecipe>> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Long userId = requireLogin();
        return ApiResponse.success(service.listFavorites(userId, PageRequest.of(page, size)));
    }

    @GetMapping("/check")
    public ApiResponse<Boolean> check(@RequestParam String recipeName) {
        Long userId = requireLogin();
        return ApiResponse.success(service.isFavorited(userId, recipeName));
    }

    @PostMapping
    public ApiResponse<FavoriteRecipe> add(@RequestBody Map<String, String> body) {
        Long userId = requireLogin();
        FavoriteRecipe fav = service.addFavorite(userId,
                body.get("recipeName"),
                body.get("recipeJson"),
                body.get("summary"));
        return ApiResponse.success(fav);
    }

    @DeleteMapping("/{id}")
    public ApiResponse<String> delete(@PathVariable Long id) {
        Long userId = requireLogin();
        service.removeFavorite(userId, id);
        return ApiResponse.success("已取消收藏");
    }

    @DeleteMapping
    public ApiResponse<String> deleteByName(@RequestParam String recipeName) {
        Long userId = requireLogin();
        service.removeFavoriteByName(userId, recipeName);
        return ApiResponse.success("已取消收藏");
    }

    private Long requireLogin() {
        Long userId = authService.getCurrentUserId();
        if (userId == null) throw new AiApiException(401, "请先登录");
        return userId;
    }
}