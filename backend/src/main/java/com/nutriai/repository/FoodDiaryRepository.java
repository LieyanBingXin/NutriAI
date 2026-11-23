package com.nutriai.repository;

import com.nutriai.entity.FoodDiary;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface FoodDiaryRepository extends JpaRepository<FoodDiary, Long> {

    Page<FoodDiary> findByUserIdOrderByRecordDateDesc(Long userId, Pageable pageable);

    Page<FoodDiary> findByUserIdAndRecordDateOrderByCreatedAtDesc(Long userId, LocalDate recordDate, Pageable pageable);

    Optional<FoodDiary> findFirstByUserIdOrderByCreatedAtDesc(Long userId);

    List<FoodDiary> findByUserIdAndRecordDate(Long userId, LocalDate recordDate);

    @Query("SELECT fd FROM FoodDiary fd WHERE fd.user.id = :userId AND fd.recordDate >= :since ORDER BY fd.recordDate ASC")
    List<FoodDiary> findByUserIdAndRecordDateAfterOrderByRecordDateAsc(Long userId, LocalDate since);

    List<FoodDiary> findByUserIdAndRecordDateGreaterThanEqualOrderByRecordDateAsc(Long userId, LocalDate since);

    List<FoodDiary> findByUserIdAndRecordDateBetween(Long userId, LocalDate start, LocalDate end);

    @Query("SELECT fd FROM FoodDiary fd WHERE fd.user.id = :userId ORDER BY fd.recordDate DESC")
    List<FoodDiary> findAllByUserIdOrderByRecordDateDesc(Long userId);

    long countByUserId(Long userId);
}