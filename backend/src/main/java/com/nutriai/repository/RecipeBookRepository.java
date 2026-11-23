package com.nutriai.repository;

import com.nutriai.entity.RecipeBook;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RecipeBookRepository extends JpaRepository<RecipeBook, Long> {

    Page<RecipeBook> findByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);

    List<RecipeBook> findByUserIdOrderByCreatedAtDesc(Long userId);

    List<RecipeBook> findByUserIdAndMealTypeOrderByCreatedAtDesc(Long userId, String mealType);

    Page<RecipeBook> findByUserIdAndMealTypeOrderByCreatedAtDesc(Long userId, String mealType, Pageable pageable);

    boolean existsByUserIdAndName(Long userId, String name);
}