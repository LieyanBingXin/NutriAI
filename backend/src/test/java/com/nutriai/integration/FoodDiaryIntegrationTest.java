package com.nutriai.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nutriai.repository.FoodDiaryRepository;
import com.nutriai.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 食物日记链路集成测试。
 * 覆盖：认证拦截、新增/修改/删除、餐别归一化、越权防护、每日汇总、空汇总、分页列表。
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:nutriai_diary;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "nutriai.demo-data.enabled=false"
})
@AutoConfigureMockMvc
class FoodDiaryIntegrationTest {

    private static final String DATE = "2026-11-11";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private FoodDiaryRepository foodDiaryRepository;

    @BeforeEach
    void clean() {
        foodDiaryRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    @DisplayName("未登录新增记录返回 401")
    void addRequiresAuth() throws Exception {
        mockMvc.perform(post("/api/food-diary")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(foodJson("apple", "lunch", 100.0, 5.0, 20.0, 1.0)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(401));
    }

    @Test
    @DisplayName("新增记录返回并归一化餐别")
    void addRecord() throws Exception {
        MockHttpSession session = registerAndLogin("alice", "pass1234");

        mockMvc.perform(post("/api/food-diary").session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(foodJson("鸡胸肉", "lunch", 250.0, 30.0, 5.0, 8.0)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.foodName").value("鸡胸肉"))
                .andExpect(jsonPath("$.data.mealType").value("午餐"))
                .andExpect(jsonPath("$.data.calories").value(250.0))
                .andExpect(jsonPath("$.data.recordDate").value(DATE));
    }

    @Test
    @DisplayName("食物名称为空返回 400")
    void addEmptyFoodName() throws Exception {
        MockHttpSession session = registerAndLogin("bob", "pass1234");

        mockMvc.perform(post("/api/food-diary").session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(foodJson("  ", "lunch", 100.0, 5.0, 20.0, 1.0)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400));
    }

    @Test
    @DisplayName("修改记录返回最新值")
    void updateRecord() throws Exception {
        MockHttpSession session = registerAndLogin("carol", "pass1234");
        long id = addRecord(session, "apple", 100.0);

        mockMvc.perform(put("/api/food-diary/" + id).session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(foodJson("banana", "breakfast", 120.0, 3.0, 28.0, 0.5)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.foodName").value("banana"))
                .andExpect(jsonPath("$.data.mealType").value("早餐"))
                .andExpect(jsonPath("$.data.calories").value(120.0));
    }

    @Test
    @DisplayName("删除记录后列表为空")
    void deleteRecord() throws Exception {
        MockHttpSession session = registerAndLogin("dave", "pass1234");
        long id = addRecord(session, "apple", 100.0);

        mockMvc.perform(delete("/api/food-diary/" + id).session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        mockMvc.perform(get("/api/food-diary").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(0));
    }

    @Test
    @DisplayName("不能修改他人记录（403）")
    void updateOtherUserRecord() throws Exception {
        MockHttpSession alice = registerAndLogin("alice", "pass1234");
        long id = addRecord(alice, "apple", 100.0);

        MockHttpSession bob = registerAndLogin("bob", "pass1234");
        mockMvc.perform(put("/api/food-diary/" + id).session(bob)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(foodJson("steal", "lunch", 1.0, 1.0, 1.0, 1.0)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403));
    }

    @Test
    @DisplayName("不能删除他人记录（403）")
    void deleteOtherUserRecord() throws Exception {
        MockHttpSession alice = registerAndLogin("alice", "pass1234");
        long id = addRecord(alice, "apple", 100.0);

        MockHttpSession bob = registerAndLogin("bob", "pass1234");
        mockMvc.perform(delete("/api/food-diary/" + id).session(bob))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403));
    }

    @Test
    @DisplayName("每日汇总正确累加营养")
    void dailySummary() throws Exception {
        MockHttpSession session = registerAndLogin("eve", "pass1234");
        addRecord(session, "鸡蛋", 150.0);
        addRecord(session, "米饭", 250.0);

        mockMvc.perform(get("/api/food-diary/daily").session(session).param("date", DATE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.date").value(DATE))
                .andExpect(jsonPath("$.data.totalItems").value(2))
                .andExpect(jsonPath("$.data.totalCalories").value(400.0));
    }

    @Test
    @DisplayName("无记录时每日汇总返回 0")
    void dailySummaryEmpty() throws Exception {
        MockHttpSession session = registerAndLogin("frank", "pass1234");

        mockMvc.perform(get("/api/food-diary/daily").session(session).param("date", DATE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalItems").value(0))
                .andExpect(jsonPath("$.data.totalCalories").value(0.0));
    }

    @Test
    @DisplayName("列表分页返回记录")
    void listRecords() throws Exception {
        MockHttpSession session = registerAndLogin("grace", "pass1234");
        addRecord(session, "鸡蛋", 150.0);
        addRecord(session, "米饭", 250.0);

        mockMvc.perform(get("/api/food-diary").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(2))
                .andExpect(jsonPath("$.data.content").isArray())
                .andExpect(jsonPath("$.data.content.length()").value(2));
    }

    /** 新增一条食物记录并返回其 id */
    private long addRecord(MockHttpSession session, String foodName, double calories) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/food-diary").session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(foodJson(foodName, "lunch", calories, 10.0, 20.0, 5.0)))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString())
                .path("data").path("id").asLong();
    }

    private String foodJson(String foodName, String mealType,
                            Double calories, Double protein, Double carbs, Double fat) throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("foodName", foodName);
        body.put("mealType", mealType);
        body.put("amountGram", 150);
        body.put("calories", calories);
        body.put("protein", protein);
        body.put("carbs", carbs);
        body.put("fat", fat);
        body.put("recordDate", DATE);
        return objectMapper.writeValueAsString(body);
    }

    /** 注册并登录，返回已认证的 session */
    private MockHttpSession registerAndLogin(String username, String password) throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("username", username, "password", password))))
                .andExpect(status().isOk());

        MvcResult login = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("username", username, "password", password))))
                .andExpect(status().isOk())
                .andReturn();

        return (MockHttpSession) login.getRequest().getSession(false);
    }
}