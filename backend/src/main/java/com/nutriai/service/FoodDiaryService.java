package com.nutriai.service;

import com.nutriai.dto.DailyNutritionDto;
import com.nutriai.dto.FoodDiaryDto;
import com.nutriai.dto.FoodDiaryRequest;
import com.nutriai.entity.FoodDiary;
import com.nutriai.entity.User;
import com.nutriai.entity.UserProfile;
import com.nutriai.exception.AiApiException;
import com.nutriai.repository.FoodDiaryRepository;
import com.nutriai.repository.UserProfileRepository;
import com.nutriai.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class FoodDiaryService {

    private final FoodDiaryRepository foodDiaryRepository;
    private final UserRepository userRepository;
    private final UserProfileRepository userProfileRepository;

    @Transactional
    public FoodDiaryDto addRecord(Long userId, FoodDiaryRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new AiApiException(404, "用户不存在"));

        FoodDiary record = new FoodDiary();
        record.setUser(user);
        record.setFoodName(request.getFoodName());
        record.setMealType(FoodDiaryDto.normalizeMealType(request.getMealType()));
        record.setAmountGram(request.getAmountGram() != null ? request.getAmountGram() : 0.0);
        record.setCalories(request.getCalories());
        record.setProtein(request.getProtein());
        record.setCarbs(request.getCarbs());
        record.setFat(request.getFat());
        record.setRecordDate(request.getRecordDate() != null ? request.getRecordDate() : LocalDate.now());
        record.setNote(request.getNote());

        record = foodDiaryRepository.save(record);
        log.info("食物日记新增: userId={}, food={}, date={}", userId, record.getFoodName(), record.getRecordDate());
        return FoodDiaryDto.from(record);
    }

    @Transactional
    public FoodDiaryDto updateRecord(Long userId, Long recordId, FoodDiaryRequest request) {
        FoodDiary record = foodDiaryRepository.findById(recordId)
                .orElseThrow(() -> new AiApiException(404, "记录不存在"));
        if (!record.getUser().getId().equals(userId)) {
            throw new AiApiException(403, "无权修改此记录");
        }

        record.setFoodName(request.getFoodName());
        record.setMealType(FoodDiaryDto.normalizeMealType(request.getMealType()));
        record.setAmountGram(request.getAmountGram());
        record.setCalories(request.getCalories());
        record.setProtein(request.getProtein());
        record.setCarbs(request.getCarbs());
        record.setFat(request.getFat());
        if (request.getRecordDate() != null) {
            record.setRecordDate(request.getRecordDate());
        }
        record.setNote(request.getNote());

        record = foodDiaryRepository.save(record);
        log.info("食物日记修改: userId={}, recordId={}", userId, recordId);
        return FoodDiaryDto.from(record);
    }

    @Transactional
    public void deleteRecord(Long userId, Long recordId) {
        FoodDiary record = foodDiaryRepository.findById(recordId)
                .orElseThrow(() -> new AiApiException(404, "记录不存在"));
        if (!record.getUser().getId().equals(userId)) {
            throw new AiApiException(403, "无权删除此记录");
        }
        foodDiaryRepository.delete(record);
        log.info("食物日记删除: userId={}, recordId={}", userId, recordId);
    }

    public Page<FoodDiaryDto> listRecords(Long userId, Pageable pageable, LocalDate date) {
        Page<FoodDiary> page = date != null
                ? foodDiaryRepository.findByUserIdAndRecordDateOrderByCreatedAtDesc(userId, date, pageable)
                : foodDiaryRepository.findByUserIdOrderByRecordDateDesc(userId, pageable);
        List<FoodDiaryDto> dtos = page.getContent().stream().map(FoodDiaryDto::from).toList();
        return new PageImpl<>(dtos, pageable, page.getTotalElements());
    }

    /** 返回用户最近一条饮食记录（用于撤销） */
    public java.util.Optional<FoodDiaryDto> getLatestRecord(Long userId) {
        return foodDiaryRepository.findFirstByUserIdOrderByCreatedAtDesc(userId)
                .map(FoodDiaryDto::from);
    }

    public DailyNutritionDto getDailySummary(Long userId, LocalDate date) {
        LocalDate target = date != null ? date : LocalDate.now();
        List<FoodDiary> items = foodDiaryRepository.findByUserIdAndRecordDate(userId, target);

        double totalCal = 0, totalPro = 0, totalCar = 0, totalFat = 0;
        for (FoodDiary fd : items) {
            if (fd.getCalories() != null) totalCal += fd.getCalories();
            if (fd.getProtein() != null) totalPro += fd.getProtein();
            if (fd.getCarbs() != null) totalCar += fd.getCarbs();
            if (fd.getFat() != null) totalFat += fd.getFat();
        }

        double avgCal = items.isEmpty() ? 0 : totalCal / items.size();

        DailyNutritionDto dto = new DailyNutritionDto(
                target.toString(),
                items.size(),
                Math.round(totalCal * 10) / 10.0,
                Math.round(totalPro * 10) / 10.0,
                Math.round(totalCar * 10) / 10.0,
                Math.round(totalFat * 10) / 10.0,
                Math.round(avgCal * 10) / 10.0,
                items.stream().map(FoodDiaryDto::from).toList()
        );

        attachGoalsAndPercents(userProfileRepository.findByUserId(userId).orElse(null), dto);
        return dto;
    }

    public List<DailyNutritionDto> getWeeklySummary(Long userId, LocalDate endDate) {
        LocalDate end = endDate != null ? endDate : LocalDate.now();
        LocalDate start = end.minusDays(6);

        List<FoodDiary> all = foodDiaryRepository
                .findByUserIdAndRecordDateBetween(userId, start, end);
        UserProfile profile = userProfileRepository.findByUserId(userId).orElse(null);

        List<DailyNutritionDto> result = new ArrayList<>();
        for (int i = 0; i < 7; i++) {
            LocalDate d = start.plusDays(i);
            LocalDate finalD = d;
            List<FoodDiary> items = all.stream()
                    .filter(fd -> fd.getRecordDate().equals(finalD))
                    .toList();

            double totalCal = 0, totalPro = 0, totalCar = 0, totalFat = 0;
            for (FoodDiary fd : items) {
                if (fd.getCalories() != null) totalCal += fd.getCalories();
                if (fd.getProtein() != null) totalPro += fd.getProtein();
                if (fd.getCarbs() != null) totalCar += fd.getCarbs();
                if (fd.getFat() != null) totalFat += fd.getFat();
            }
            double avgCal = items.isEmpty() ? 0 : totalCal / items.size();

            DailyNutritionDto dto = new DailyNutritionDto(
                    d.toString(),
                    items.size(),
                    Math.round(totalCal * 10) / 10.0,
                    Math.round(totalPro * 10) / 10.0,
                    Math.round(totalCar * 10) / 10.0,
                    Math.round(totalFat * 10) / 10.0,
                    Math.round(avgCal * 10) / 10.0,
                    items.stream().map(FoodDiaryDto::from).toList()
            );
            attachGoalsAndPercents(profile, dto);
            result.add(dto);
        }
        return result;
    }

    public List<FoodDiaryDto> listForChart(Long userId, Integer days) {
        LocalDate since = days != null
                ? LocalDate.now().minusDays(days)
                : LocalDate.now().minusDays(30);
        List<FoodDiary> records = foodDiaryRepository
                .findByUserIdAndRecordDateGreaterThanEqualOrderByRecordDateAsc(userId, since);
        return records.stream().map(FoodDiaryDto::from).toList();
    }

    /** 导出用：返回用户全部记录（按日期倒序） */
    public List<FoodDiaryDto> listAll(Long userId) {
        return foodDiaryRepository.findAllByUserIdOrderByRecordDateDesc(userId).stream()
                .map(FoodDiaryDto::from)
                .toList();
    }

    private void attachGoalsAndPercents(UserProfile profile, DailyNutritionDto dto) {
        if (profile == null) return;
        dto.setCalorieGoal(profile.getDailyCalorieGoal());
        dto.setProteinGoal(profile.getProteinGoal());
        dto.setCarbsGoal(profile.getCarbsGoal());
        dto.setFatGoal(profile.getFatGoal());

        dto.setCaloriePercent(calcPercent(dto.getTotalCalories(), profile.getDailyCalorieGoal()));
        dto.setProteinPercent(calcPercent(dto.getTotalProtein(), profile.getProteinGoal()));
        dto.setCarbsPercent(calcPercent(dto.getTotalCarbs(), profile.getCarbsGoal()));
        dto.setFatPercent(calcPercent(dto.getTotalFat(), profile.getFatGoal()));
    }

    private Double calcPercent(Double actual, Double goal) {
        if (actual == null || goal == null || goal == 0) return null;
        return Math.round((actual / goal) * 1000) / 10.0;
    }
}