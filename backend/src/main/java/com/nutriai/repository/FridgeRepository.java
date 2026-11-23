package com.nutriai.repository;

import com.nutriai.entity.FridgeItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface FridgeRepository extends JpaRepository<FridgeItem, Long> {
    List<FridgeItem> findByUserIdOrderByCreatedAtDesc(Long userId);
    Optional<FridgeItem> findByUserIdAndIngredientIgnoreCase(Long userId, String ingredient);
    void deleteByUserIdAndIngredientIgnoreCase(Long userId, String ingredient);
    void deleteByUserId(Long userId);
    boolean existsByUserIdAndIngredientIgnoreCase(Long userId, String ingredient);
}