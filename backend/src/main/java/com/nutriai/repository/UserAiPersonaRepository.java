package com.nutriai.repository;

import com.nutriai.entity.UserAiPersona;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface UserAiPersonaRepository extends JpaRepository<UserAiPersona, Long> {

    List<UserAiPersona> findByUserIdOrderByCreatedAtAsc(Long userId);

    boolean existsByUserIdAndTagName(Long userId, String tagName);

    void deleteByUserIdAndId(Long userId, Long id);

    void deleteByUserIdAndTagName(Long userId, String tagName);

    void deleteAllByUserId(Long userId);
}