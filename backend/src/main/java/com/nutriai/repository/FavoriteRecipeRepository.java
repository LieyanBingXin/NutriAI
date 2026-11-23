package com.nutriai.repository;

import com.nutriai.entity.FavoriteRecipe;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface FavoriteRecipeRepository extends JpaRepository<FavoriteRecipe, Long> {

    Page<FavoriteRecipe> findByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);

    List<FavoriteRecipe> findByUserId(Long userId);

    boolean existsByUserIdAndRecipeName(Long userId, String recipeName);
}