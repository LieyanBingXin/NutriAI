package com.nutriai.controller;

import com.nutriai.service.FoodRecognizerService;
import com.nutriai.dto.ApiResponse;
import com.nutriai.dto.DailyNutritionDto;
import com.nutriai.dto.FoodDiaryDto;
import com.nutriai.dto.FoodRecognitionResult;
import com.nutriai.dto.FoodDiaryRequest;
import com.nutriai.exception.AiApiException;
import com.nutriai.service.AuthService;
import com.nutriai.service.FoodDiaryService;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.*;

import java.io.PrintWriter;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/food-diary")
@RequiredArgsConstructor
public class FoodDiaryController {

    private final FoodDiaryService foodDiaryService;
    private final AuthService authService;
    private final FoodRecognizerService foodRecognizerService;

    @PostMapping
    public ApiResponse<FoodDiaryDto> add(@Valid @RequestBody FoodDiaryRequest request) {
        Long userId = requireUserId();
        return ApiResponse.success(foodDiaryService.addRecord(userId, request));
    }

    @PutMapping("/{id}")
    public ApiResponse<FoodDiaryDto> update(@PathVariable Long id,
                                            @Valid @RequestBody FoodDiaryRequest request) {
        Long userId = requireUserId();
        return ApiResponse.success(foodDiaryService.updateRecord(userId, id, request));
    }

    @GetMapping
    public ApiResponse<Page<FoodDiaryDto>> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        Long userId = requireUserId();
        return ApiResponse.success(foodDiaryService.listRecords(userId, PageRequest.of(page, size), date));
    }

    @GetMapping("/daily")
    public ApiResponse<DailyNutritionDto> daily(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        Long userId = requireUserId();
        return ApiResponse.success(foodDiaryService.getDailySummary(userId, date));
    }

    @GetMapping("/weekly")
    public ApiResponse<List<DailyNutritionDto>> weekly(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        Long userId = requireUserId();
        return ApiResponse.success(foodDiaryService.getWeeklySummary(userId, endDate));
    }

    @GetMapping("/weekly-compare")
    public ApiResponse<Map<String, Object>> weeklyCompare() {
        Long userId = requireUserId();
        Map<String, Object> result = new LinkedHashMap<>();
        LocalDate now = LocalDate.now();
        LocalDate thisWeekEnd = now;
        LocalDate lastWeekEnd = now.minusWeeks(1);

        List<DailyNutritionDto> thisWeek = foodDiaryService.getWeeklySummary(userId, thisWeekEnd);
        List<DailyNutritionDto> lastWeek = foodDiaryService.getWeeklySummary(userId, lastWeekEnd);

        double[] thisTotals = sumWeek(thisWeek);
        double[] lastTotals = sumWeek(lastWeek);

        Map<String, Object> thisMap = new LinkedHashMap<>();
        thisMap.put("days", (int) thisTotals[0]);
        thisMap.put("totalCal", Math.round(thisTotals[1]));
        thisMap.put("totalProtein", Math.round(thisTotals[2] * 10) / 10.0);
        thisMap.put("totalCarbs", Math.round(thisTotals[3] * 10) / 10.0);
        thisMap.put("totalFat", Math.round(thisTotals[4] * 10) / 10.0);

        Map<String, Object> lastMap = new LinkedHashMap<>();
        lastMap.put("days", (int) lastTotals[0]);
        lastMap.put("totalCal", Math.round(lastTotals[1]));
        lastMap.put("totalProtein", Math.round(lastTotals[2] * 10) / 10.0);
        lastMap.put("totalCarbs", Math.round(lastTotals[3] * 10) / 10.0);
        lastMap.put("totalFat", Math.round(lastTotals[4] * 10) / 10.0);

        result.put("thisWeek", thisMap);
        result.put("lastWeek", lastMap);

        long diffCal = Math.round(thisTotals[1] - lastTotals[1]);
        String trend = diffCal > 100 ? "up" : (diffCal < -100 ? "down" : "stable");
        result.put("calTrend", trend);
        result.put("calDiff", Math.abs(diffCal));

        return ApiResponse.success(result);
    }

    private double[] sumWeek(List<DailyNutritionDto> week) {
        int days = 0;
        double totalCal = 0, totalProtein = 0, totalCarbs = 0, totalFat = 0;
        for (DailyNutritionDto d : week) {
            if (d.getTotalItems() > 0) {
                days++;
                totalCal += d.getTotalCalories() != null ? d.getTotalCalories() : 0;
                totalProtein += d.getTotalProtein() != null ? d.getTotalProtein() : 0;
                totalCarbs += d.getTotalCarbs() != null ? d.getTotalCarbs() : 0;
                totalFat += d.getTotalFat() != null ? d.getTotalFat() : 0;
            }
        }
        return new double[]{days, totalCal, totalProtein, totalCarbs, totalFat};
    }

    @GetMapping("/chart")
    public ApiResponse<List<FoodDiaryDto>> chart(
            @RequestParam(required = false) Integer days) {
        Long userId = requireUserId();
        return ApiResponse.success(foodDiaryService.listForChart(userId, days));
    }

    @PostMapping("/recognize")
    public ApiResponse<FoodRecognitionResult> recognize(@RequestBody Map<String, String> body) {
        requireUserId();
        String description = body.get("description");
        if (description == null || description.isBlank()) {
            throw new AiApiException(400, "请输入食物描述");
        }
        FoodRecognitionResult result = foodRecognizerService.recognize(description);
        return ApiResponse.success(result);
    }

    @DeleteMapping("/{id}")
    public ApiResponse<String> delete(@PathVariable Long id) {
        Long userId = requireUserId();
        foodDiaryService.deleteRecord(userId, id);
        return ApiResponse.success("已删除");
    }

    @GetMapping("/export")
    public void exportCsv(HttpServletResponse response) {
        Long userId = requireUserId();
        response.setContentType("text/csv; charset=UTF-8");
        response.setHeader(HttpHeaders.CONTENT_DISPOSITION,
                "attachment; filename=\"food_diary.csv\"; filename*=UTF-8''" + "food_diary.csv");
        response.setCharacterEncoding("UTF-8");
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
        try (PrintWriter w = response.getWriter()) {
            w.println("\uFEFF日期,餐别,食物,重量(g),热量(kcal),蛋白质(g),碳水(g),脂肪(g),备注");
            List<FoodDiaryDto> all = foodDiaryService.listAll(userId);
            for (FoodDiaryDto r : all) {
                w.printf("%s,%s,%s,%s,%s,%s,%s,%s,%s%n",
                        csvCell(r.getRecordDate() != null ? r.getRecordDate().toString() : ""),
                        csvCell(r.getMealType()),
                        csvCell(r.getFoodName()),
                        numberCell(r.getAmountGram()),
                        numberCell(r.getCalories()),
                        numberCell(r.getProtein()),
                        numberCell(r.getCarbs()),
                        numberCell(r.getFat()),
                        csvCell(r.getNote()));
            }
        } catch (Exception e) {
            throw new AiApiException(500, "导出CSV失败");
        }
    }

    /** CSV 单元格：双引号转义，并防止 Excel 公式注入（= + - @ 开头）。 */
    private String csvCell(String value) {
        if (value == null) return "\"\"";
        String v = value.replace("\"", "\"\"");
        if (!v.isEmpty() && "=+-@\t\r".indexOf(v.charAt(0)) >= 0) {
            v = "'" + v;
        }
        return "\"" + v + "\"";
    }

    private String numberCell(Double value) {
        return value == null ? "" : String.valueOf(value);
    }

    private Long requireUserId() {
        Long userId = authService.getCurrentUserId();
        if (userId == null) {
            throw new AiApiException(401, "请先登录");
        }
        return userId;
    }
}