package com.nutriai.repository;

import com.nutriai.entity.RailState;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface RailStateRepository extends JpaRepository<RailState, Long> {
    Optional<RailState> findByUserId(Long userId);
}