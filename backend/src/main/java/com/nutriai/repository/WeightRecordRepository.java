package com.nutriai.repository;

import com.nutriai.entity.WeightRecord;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface WeightRecordRepository extends JpaRepository<WeightRecord, Long> {

    Page<WeightRecord> findByUserIdOrderByRecordDateDesc(Long userId, Pageable pageable);

    Optional<WeightRecord> findByUserIdAndRecordDate(Long userId, LocalDate recordDate);

    @Query("SELECT wr FROM WeightRecord wr WHERE wr.user.id = :userId AND wr.recordDate >= :since ORDER BY wr.recordDate ASC")
    List<WeightRecord> findByUserIdAndRecordDateAfterOrderByRecordDateAsc(Long userId, LocalDate since);

    @Query("SELECT wr FROM WeightRecord wr WHERE wr.user.id = :userId ORDER BY wr.recordDate DESC")
    List<WeightRecord> findAllByUserIdOrderByRecordDateDesc(Long userId);

    Optional<WeightRecord> findFirstByUserIdOrderByRecordDateAsc(Long userId);

    Optional<WeightRecord> findFirstByUserIdOrderByRecordDateDesc(Long userId);

    long countByUserId(Long userId);
}