package com.nutriai.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@Entity
@Table(name = "recipe_book")
public class RecipeBook {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "user_id", insertable = false, updatable = false)
    private Long userId;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(length = 500)
    private String description;

    @Column(length = 20)
    private String mealType;

    @Column(columnDefinition = "TEXT")
    private String ingredientsText;

    @Column(columnDefinition = "TEXT")
    private String stepsText;

    private Double calories;
    private Double proteinGrams;
    private Double carbsGrams;
    private Double fatGrams;

    @Column(length = 300)
    private String tags;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;
}