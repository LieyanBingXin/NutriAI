package com.nutriai.controller;

import com.nutriai.dto.ApiResponse;
import com.nutriai.dto.HistoryDetailDto;
import com.nutriai.dto.HistoryRecordDto;
import com.nutriai.exception.AiApiException;
import com.nutriai.service.AuthService;
import com.nutriai.service.HistoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/recommend/history")
@RequiredArgsConstructor
public class HistoryController {

    private final HistoryService historyService;
    private final AuthService authService;

    @GetMapping
    public ApiResponse<Map<String, Object>> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        Long userId = authService.getCurrentUserId();
        if (userId == null) {
            throw new AiApiException(401, "请先登录");
        }
        Page<HistoryRecordDto> records = historyService.listRecords(userId, page, size);
        Map<String, Object> result = new HashMap<>();
        result.put("content", records.getContent());
        result.put("page", records.getNumber());
        result.put("size", records.getSize());
        result.put("totalElements", records.getTotalElements());
        result.put("totalPages", records.getTotalPages());
        result.put("first", records.isFirst());
        result.put("last", records.isLast());
        return ApiResponse.success(result);
    }

    @GetMapping("/{id}")
    public ApiResponse<HistoryDetailDto> getDetail(@PathVariable Long id) {
        Long userId = authService.getCurrentUserId();
        if (userId == null) {
            throw new AiApiException(401, "请先登录");
        }
        return ApiResponse.success(historyService.getDetail(userId, id));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<String> delete(@PathVariable Long id) {
        Long userId = authService.getCurrentUserId();
        if (userId == null) {
            throw new AiApiException(401, "请先登录");
        }
        historyService.delete(userId, id);
        return ApiResponse.success("删除成功");
    }
}