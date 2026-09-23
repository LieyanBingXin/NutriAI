package com.nutriai.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nutriai.dto.UserProfileDto;
import com.nutriai.entity.User;
import com.nutriai.entity.UserProfile;
import com.nutriai.repository.UserProfileRepository;
import com.nutriai.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class UserProfileService {

    private final UserProfileRepository profileRepository;
    private final UserRepository userRepository;
    private final ObjectMapper objectMapper;

    @Transactional
    public UserProfileDto saveProfile(Long userId, UserProfileDto dto) {
        User user = userRepository.findById(userId).orElseThrow();

        UserProfile profile = profileRepository.findByUserId(userId).orElse(new UserProfile());
        profile.setUser(user);
        profile.setGender(dto.getGender());
        profile.setAge(dto.getAge());
        profile.setHeightCm(dto.getHeightCm());
        profile.setWeightKg(dto.getWeightKg());
        profile.setActivityLevel(dto.getActivityLevel());
        profile.setHealthGoal(dto.getHealthGoal());
        profile.setTastePreference(dto.getTastePreference());
        profile.setFoodPreferencesJson(toJson(dto.getFoodPreferences()));
        profile.setFoodAllergiesJson(toJson(dto.getFoodAllergies()));

        profile = profileRepository.save(profile);
        return toDto(profile);
    }

    public UserProfileDto getProfile(Long userId) {
        return profileRepository.findByUserId(userId)
                .map(this::toDto)
                .orElse(null);
    }

    @Transactional
    public void deleteProfile(Long userId) {
        profileRepository.deleteByUserId(userId);
    }

    private UserProfileDto toDto(UserProfile p) {
        UserProfileDto dto = new UserProfileDto();
        dto.setId(p.getId());
        dto.setGender(p.getGender());
        dto.setAge(p.getAge());
        dto.setHeightCm(p.getHeightCm());
        dto.setWeightKg(p.getWeightKg());
        dto.setActivityLevel(p.getActivityLevel());
        dto.setHealthGoal(p.getHealthGoal());
        dto.setTastePreference(p.getTastePreference());
        dto.setFoodPreferences(fromJson(p.getFoodPreferencesJson()));
        dto.setFoodAllergies(fromJson(p.getFoodAllergiesJson()));
        dto.setUpdatedAt(p.getUpdatedAt());
        return dto;
    }

    private String toJson(List<String> list) {
        if (list == null) return "[]";
        try {
            return objectMapper.writeValueAsString(list);
        } catch (JsonProcessingException e) {
            return "[]";
        }
    }

    private List<String> fromJson(String json) {
        if (json == null || json.isBlank()) return List.of();
        try {
            return objectMapper.readValue(json, new TypeReference<List<String>>() {});
        } catch (JsonProcessingException e) {
            return List.of();
        }
    }
}