package com.nutriai.entity;

import java.time.LocalDateTime;

import org.hibernate.annotations.UpdateTimestamp;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@Entity
@Table(name = "user_profiles")
public class UserProfile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    @Column(length = 10)
    private String gender;

    private Integer age;

    private Double heightCm;

    private Double weightKg;

    @Column(length = 20)
    private String activityLevel;

    @Column(length = 30)
    private String healthGoal;

    @Column(name = "taste_preference", columnDefinition = "TEXT")
    private String tastePreferencesJson;

    @Column(length = 30)
    private String region;

    private Double dailyCalorieGoal;

    private Double proteinGoal;

    private Double carbsGoal;

    private Double fatGoal;

    @Column(columnDefinition = "TEXT")
    private String foodPreferencesJson;

    @Column(columnDefinition = "TEXT")
    private String foodAllergiesJson;

    @Column(columnDefinition = "TEXT")
    private String healthConditionsJson;

    @UpdateTimestamp
    private LocalDateTime updatedAt;
}