package com.nutriai.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@Entity
@Table(name = "food_diaries", indexes = {
        @Index(name = "idx_fooddiary_user_date", columnList = "user_id, record_date DESC")
})
public class FoodDiary {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false, length = 100)
    private String foodName;

    @Column(length = 20)
    private String mealType;

    private Double amountGram;

    private Double calories;

    private Double protein;

    private Double carbs;

    private Double fat;

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