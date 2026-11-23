package com.nutriai.service;

import com.nutriai.entity.FavoriteRecipe;
import com.nutriai.entity.User;
import com.nutriai.exception.AiApiException;
import com.nutriai.repository.FavoriteRecipeRepository;
import com.nutriai.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class FavoriteRecipeService {

    private final FavoriteRecipeRepository repository;
    private final UserRepository userRepository;

    public Page<FavoriteRecipe> listFavorites(Long userId, Pageable pageable) {
        return repository.findByUserIdOrderByCreatedAtDesc(userId, pageable);
    }

    @Transactional
    public FavoriteRecipe addFavorite(Long userId, String recipeName, String recipeJson, String summary) {
        if (repository.existsByUserIdAndRecipeName(userId, recipeName)) {
            throw new AiApiException(400, "该食谱已收藏");
        }
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new AiApiException(404, "用户不存在"));
        FavoriteRecipe fav = new FavoriteRecipe(user, recipeName, recipeJson, summary);
        return repository.save(fav);
    }

    @Transactional
    public void removeFavorite(Long userId, Long favoriteId) {
        FavoriteRecipe fav = repository.findById(favoriteId)
                .orElseThrow(() -> new AiApiException(404, "收藏不存在"));
        if (!fav.getUser().getId().equals(userId)) {
            throw new AiApiException(403, "无权操作");
        }
        repository.delete(fav);
    }

    @Transactional
    public void removeFavoriteByName(Long userId, String recipeName) {
        repository.findByUserId(userId).stream()
                .filter(f -> f.getRecipeName().equals(recipeName))
                .findFirst()
                .ifPresent(repository::delete);
    }

    public boolean isFavorited(Long userId, String recipeName) {
        return repository.existsByUserIdAndRecipeName(userId, recipeName);
    }
}