package com.nutriai.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@Entity
@Table(name = "recommendation_records")
public class RecommendationRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(columnDefinition = "TEXT")
    private String profileJson;

    @Column(columnDefinition = "TEXT")
    private String responseJson;

    @Column(length = 50)
    private String aiProvider;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;
}