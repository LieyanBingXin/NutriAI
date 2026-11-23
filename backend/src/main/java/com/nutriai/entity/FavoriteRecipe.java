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
@Table(name = "favorite_recipes")
public class FavoriteRecipe {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false, length = 200)
    private String recipeName;

    @Column(columnDefinition = "TEXT")
    private String recipeJson;

    @Column(length = 500)
    private String summary;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;

    public FavoriteRecipe(User user, String recipeName, String recipeJson, String summary) {
        this.user = user;
        this.recipeName = recipeName;
        this.recipeJson = recipeJson;
        this.summary = summary;
    }
}