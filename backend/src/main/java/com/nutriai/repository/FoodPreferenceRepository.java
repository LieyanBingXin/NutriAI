package com.nutriai.repository;

import com.nutriai.entity.FoodPreference;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface FoodPreferenceRepository extends JpaRepository<FoodPreference, Long> {

    List<FoodPreference> findByUserId(Long userId);

    Optional<FoodPreference> findByUserIdAndFoodName(Long userId, String foodName);

    List<FoodPreference> findByUserIdAndPreference(Long userId, String preference);

    void deleteByUserIdAndFoodName(Long userId, String foodName);
}