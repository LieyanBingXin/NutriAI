package com.nutriai.repository;

import com.nutriai.entity.RecommendationRecord;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RecommendationRecordRepository extends JpaRepository<RecommendationRecord, Long> {

    Page<RecommendationRecord> findByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);
}