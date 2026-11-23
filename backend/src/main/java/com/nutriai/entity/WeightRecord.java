package com.nutriai.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@Entity
@Table(name = "weight_records", indexes = {
        @Index(name = "idx_weight_user_date", columnList = "user_id, record_date DESC")
})
public class WeightRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false)
    private Double weightKg;

    private LocalDate recordDate;

    @Column(length = 200)
    private String note;

    private LocalDateTime createdAt;

    @PrePersist
    public void prePersist() {
        if (recordDate == null) recordDate = LocalDate.now();
        if (createdAt == null) createdAt = LocalDateTime.now();
    }
}