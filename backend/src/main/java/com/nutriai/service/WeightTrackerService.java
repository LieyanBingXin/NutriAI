package com.nutriai.service;

import com.nutriai.dto.WeightRecordDto;
import com.nutriai.dto.WeightRequest;
import com.nutriai.dto.WeightStatsDto;
import com.nutriai.entity.User;
import com.nutriai.entity.WeightRecord;
import com.nutriai.exception.AiApiException;
import com.nutriai.repository.UserRepository;
import com.nutriai.repository.WeightRecordRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class WeightTrackerService {

    private final WeightRecordRepository weightRecordRepository;
    private final UserRepository userRepository;

    @Transactional
    public WeightRecordDto addRecord(Long userId, WeightRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new AiApiException(404, "用户不存在"));

        LocalDate date = request.getRecordDate() != null ? request.getRecordDate() : LocalDate.now();

        WeightRecord existing = weightRecordRepository.findByUserIdAndRecordDate(userId, date).orElse(null);
        if (existing != null) {
            existing.setWeightKg(request.getWeightKg());
            existing.setNote(request.getNote());
            return WeightRecordDto.from(weightRecordRepository.save(existing));
        }

        WeightRecord record = new WeightRecord();
        record.setUser(user);
        record.setWeightKg(request.getWeightKg());
        record.setRecordDate(date);
        record.setNote(request.getNote());
        record = weightRecordRepository.save(record);

        log.info("体重记录新增: userId={}, weight={}, date={}", userId, record.getWeightKg(), date);
        return WeightRecordDto.from(record);
    }

    @Transactional
    public void deleteRecord(Long userId, Long recordId) {
        WeightRecord record = weightRecordRepository.findById(recordId)
                .orElseThrow(() -> new AiApiException(404, "记录不存在"));
        if (!record.getUser().getId().equals(userId)) {
            throw new AiApiException(403, "无权删除此记录");
        }
        weightRecordRepository.delete(record);
        log.info("体重记录删除: userId={}, recordId={}", userId, recordId);
    }

    public Page<WeightRecordDto> listRecords(Long userId, Pageable pageable) {
        Page<WeightRecord> page = weightRecordRepository.findByUserIdOrderByRecordDateDesc(userId, pageable);
        List<WeightRecordDto> dtos = page.getContent().stream().map(WeightRecordDto::from).toList();
        return new PageImpl<>(dtos, pageable, page.getTotalElements());
    }

    public List<WeightRecordDto> listRecordsForChart(Long userId, Integer days) {
        LocalDate since = days != null
                ? LocalDate.now().minusDays(days)
                : LocalDate.now().minusDays(365);
        List<WeightRecord> records = weightRecordRepository
                .findByUserIdAndRecordDateAfterOrderByRecordDateAsc(userId, since);
        return records.stream().map(WeightRecordDto::from).toList();
    }

    /** 导出用：返回用户全部体重记录（按日期倒序） */
    public List<WeightRecordDto> listAll(Long userId) {
        return weightRecordRepository.findAllByUserIdOrderByRecordDateDesc(userId).stream()
                .map(WeightRecordDto::from)
                .toList();
    }

    public WeightStatsDto getStats(Long userId) {
        List<WeightRecord> all = weightRecordRepository.findAllByUserIdOrderByRecordDateDesc(userId);
        if (all.isEmpty()) {
            return new WeightStatsDto(0, null, null, null, null, null, null, null, null, null);
        }

        WeightRecord latest = all.get(0);
        WeightRecord earliest = all.get(all.size() - 1);

        double sum = 0, min = Double.MAX_VALUE, max = -Double.MAX_VALUE;
        for (WeightRecord r : all) {
            sum += r.getWeightKg();
            if (r.getWeightKg() < min) min = r.getWeightKg();
            if (r.getWeightKg() > max) max = r.getWeightKg();
        }
        double avg = sum / all.size();

        double change = latest.getWeightKg() - earliest.getWeightKg();
        double changePct = earliest.getWeightKg() != 0
                ? (change / earliest.getWeightKg()) * 100
                : 0;

        return new WeightStatsDto(
                all.size(),
                earliest.getWeightKg(), earliest.getRecordDate().toString(),
                latest.getWeightKg(), latest.getRecordDate().toString(),
                Math.round(change * 10) / 10.0,
                Math.round(changePct * 100) / 100.0,
                Math.round(avg * 10) / 10.0,
                min,
                max
        );
    }
}