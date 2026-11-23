package com.nutriai.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

/**
 * 便利贴 rail 的跨设备持久化快照 —— 每个用户一条记录，整体存 JSON。
 * JSON 结构：{ likes:[], dislikes:[], fridge:[], gap:[], custom:[] }
 */
@Data
@NoArgsConstructor
@Entity
@Table(name = "rail_states")
public class RailState {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    @Lob
    @Column(columnDefinition = "TEXT")
    private String data;

    @UpdateTimestamp
    private LocalDateTime updatedAt;

    public RailState(User user, String data) {
        this.user = user;
        this.data = data;
    }
}