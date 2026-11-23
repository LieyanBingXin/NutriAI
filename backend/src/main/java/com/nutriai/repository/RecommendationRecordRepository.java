package com.nutriai.repository;

import com.nutriai.entity.RecommendationRecord;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RecommendationRecordRepository extends JpaRepository<RecommendationRecord, Long> {

    Page<RecommendationRecord> findByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);

    long countByUserId(Long userId);

    @Query("""
            SELECT r FROM RecommendationRecord r
            WHERE r.user.id = :userId
              AND (:keyword = '' OR LOWER(r.responseJson) LIKE LOWER(CONCAT('%', :keyword, '%')))
            """)
    Page<RecommendationRecord> searchByUserId(@Param("userId") Long userId,
                                              @Param("keyword") String keyword,
                                              Pageable pageable);
}