package com.nutriai.controller;

import com.nutriai.dto.ApiResponse;
import com.nutriai.dto.WeightRecordDto;
import com.nutriai.dto.WeightRequest;
import com.nutriai.dto.WeightStatsDto;
import com.nutriai.service.AuthService;
import com.nutriai.service.WeightTrackerService;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.*;

import java.io.PrintWriter;
import java.util.List;

@RestController
@RequestMapping("/api/weight")
@RequiredArgsConstructor
public class WeightTrackerController {

    private final WeightTrackerService weightTrackerService;
    private final AuthService authService;

    @PostMapping
    public ApiResponse<WeightRecordDto> add(@Valid @RequestBody WeightRequest request) {
        Long userId = requireUserId();
        return ApiResponse.success(weightTrackerService.addRecord(userId, request));
    }

    @GetMapping
    public ApiResponse<Page<WeightRecordDto>> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Long userId = requireUserId();
        return ApiResponse.success(weightTrackerService.listRecords(userId, PageRequest.of(page, size)));
    }

    @GetMapping("/chart")
    public ApiResponse<List<WeightRecordDto>> chart(
            @RequestParam(required = false) Integer days) {
        Long userId = requireUserId();
        return ApiResponse.success(weightTrackerService.listRecordsForChart(userId, days));
    }

    @GetMapping("/stats")
    public ApiResponse<WeightStatsDto> stats() {
        Long userId = requireUserId();
        return ApiResponse.success(weightTrackerService.getStats(userId));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<String> delete(@PathVariable Long id) {
        Long userId = requireUserId();
        weightTrackerService.deleteRecord(userId, id);
        return ApiResponse.success("已删除");
    }

    @GetMapping("/export")
    public void exportCsv(HttpServletResponse response) {
        Long userId = requireUserId();
        response.setContentType("text/csv; charset=UTF-8");
        response.setHeader(HttpHeaders.CONTENT_DISPOSITION,
                "attachment; filename=\"weight_records.csv\"; filename*=UTF-8''weight_records.csv");
        response.setCharacterEncoding("UTF-8");
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
        try (PrintWriter w = response.getWriter()) {
            w.println("\uFEFF日期,体重(kg),备注");
            List<WeightRecordDto> all = weightTrackerService.listAll(userId);
            for (WeightRecordDto r : all) {
                w.printf("%s,%s,%s%n",
                        r.getRecordDate() != null ? r.getRecordDate().toString() : "",
                        r.getWeightKg() != null ? String.format(java.util.Locale.ROOT, "%.1f", r.getWeightKg()) : "",
                        csvCell(r.getNote()));
            }
        } catch (Exception e) {
            throw new com.nutriai.exception.AiApiException(500, "导出CSV失败");
        }
    }

    private String csvCell(String value) {
        if (value == null) return "\"\"";
        String v = value.replace("\"", "\"\"");
        if (!v.isEmpty() && "=+-@\t\r".indexOf(v.charAt(0)) >= 0) {
            v = "'" + v;
        }
        return "\"" + v + "\"";
    }

    private Long requireUserId() {
        Long userId = authService.getCurrentUserId();
        if (userId == null) {
            throw new com.nutriai.exception.AiApiException(401, "请先登录");
        }
        return userId;
    }
}