package com.nutriai.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

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

    @Column(length = 20)
    private String tastePreference;

    @Column(columnDefinition = "TEXT")
    private String foodPreferencesJson;

    @Column(columnDefinition = "TEXT")
    private String foodAllergiesJson;

    @UpdateTimestamp
    private LocalDateTime updatedAt;
}