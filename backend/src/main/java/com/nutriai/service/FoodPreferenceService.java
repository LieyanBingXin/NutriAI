package com.nutriai.service;

import com.nutriai.entity.FoodPreference;
import com.nutriai.entity.User;
import com.nutriai.exception.AiApiException;
import com.nutriai.repository.FoodPreferenceRepository;
import com.nutriai.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class FoodPreferenceService {

    private final FoodPreferenceRepository repository;
    private final UserRepository userRepository;

    public List<FoodPreference> getUserPreferences(Long userId) {
        return repository.findByUserId(userId);
    }

    public String buildPreferenceContext(Long userId) {
        List<FoodPreference> prefs = repository.findByUserId(userId);
        if (prefs.isEmpty()) return "";

        List<FoodPreference> likes = prefs.stream().filter(p -> "LIKE".equals(p.getPreference())).toList();
        List<FoodPreference> dislikes = prefs.stream().filter(p -> "DISLIKE".equals(p.getPreference())).toList();

        StringBuilder sb = new StringBuilder();
        sb.append("\n\n【用户饮食偏好】\n");
        if (!likes.isEmpty()) {
            sb.append("- 喜欢/适应的食物：");
            sb.append(String.join("、", likes.stream().map(FoodPreference::getFoodName).toList()));
            sb.append("\n");
        }
        if (!dislikes.isEmpty()) {
            sb.append("- 不喜欢/需避开的食物：");
            sb.append(String.join("、", dislikes.stream().map(FoodPreference::getFoodName).toList()));
            sb.append("\n");
        }
        sb.append("请在推荐食谱时充分考虑以上偏好，优先推荐喜欢的食物，避开不喜欢的食物。");
        return sb.toString();
    }

    @Transactional
    public FoodPreference setPreference(Long userId, String foodName, String category, String preference) {
        if (preference != null && !preference.equals("LIKE") && !preference.equals("DISLIKE") && !preference.equals("NEUTRAL")) {
            throw new AiApiException(400, "偏好值只能是 LIKE、DISLIKE 或 NEUTRAL");
        }
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new AiApiException(404, "用户不存在"));

        if (preference == null || "NEUTRAL".equals(preference)) {
            repository.findByUserIdAndFoodName(userId, foodName)
                    .ifPresent(repository::delete);
            return null;
        }

        FoodPreference pref = repository.findByUserIdAndFoodName(userId, foodName)
                .orElse(new FoodPreference(user, foodName, category, preference));
        pref.setPreference(preference);
        pref.setCategory(category != null ? category : pref.getCategory());
        return repository.save(pref);
    }

    @Transactional
    public void deletePreference(Long userId, String foodName) {
        repository.deleteByUserIdAndFoodName(userId, foodName);
    }
}