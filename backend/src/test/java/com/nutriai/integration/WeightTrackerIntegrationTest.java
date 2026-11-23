package com.nutriai.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nutriai.repository.UserRepository;
import com.nutriai.repository.WeightRecordRepository;
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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 体重追踪链路集成测试。
 * 覆盖：认证拦截、新增、参数校验、同日期覆盖、统计、空统计、删除、越权防护。
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:nutriai_weight;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "nutriai.demo-data.enabled=false"
})
@AutoConfigureMockMvc
class WeightTrackerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private WeightRecordRepository weightRecordRepository;

    @BeforeEach
    void clean() {
        weightRecordRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    @DisplayName("未登录新增体重返回 401")
    void addRequiresAuth() throws Exception {
        mockMvc.perform(post("/api/weight")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(weightJson(70.5, "2026-11-11")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(401));
    }

    @Test
    @DisplayName("新增体重返回记录")
    void addRecord() throws Exception {
        MockHttpSession session = registerAndLogin("alice", "pass1234");

        mockMvc.perform(post("/api/weight").session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(weightJson(70.5, "2026-11-11")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.weightKg").value(70.5))
                .andExpect(jsonPath("$.data.recordDate").value("2026-11-11"));
    }

    @Test
    @DisplayName("体重为 0 返回 400")
    void addInvalidWeight() throws Exception {
        MockHttpSession session = registerAndLogin("bob", "pass1234");

        mockMvc.perform(post("/api/weight").session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(weightJson(0.0, "2026-11-11")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400));
    }

    @Test
    @DisplayName("未来日期返回 400")
    void addFutureDate() throws Exception {
        MockHttpSession session = registerAndLogin("carol", "pass1234");

        mockMvc.perform(post("/api/weight").session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(weightJson(70.0, "2099-01-01")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400));
    }

    @Test
    @DisplayName("同日期第二次新增覆盖前一条")
    void upsertSameDate() throws Exception {
        MockHttpSession session = registerAndLogin("dave", "pass1234");
        addWeight(session, 70.0, "2026-11-11");
        addWeight(session, 71.0, "2026-11-11");

        mockMvc.perform(get("/api/weight").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1));

        mockMvc.perform(get("/api/weight/stats").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalRecords").value(1))
                .andExpect(jsonPath("$.data.currentWeight").value(71.0));
    }

    @Test
    @DisplayName("统计正确计算平均/最值/变化")
    void stats() throws Exception {
        MockHttpSession session = registerAndLogin("eve", "pass1234");
        addWeight(session, 70.0, "2026-11-09");
        addWeight(session, 72.0, "2026-11-10");
        addWeight(session, 71.0, "2026-11-11");

        mockMvc.perform(get("/api/weight/stats").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalRecords").value(3))
                .andExpect(jsonPath("$.data.firstWeight").value(70.0))
                .andExpect(jsonPath("$.data.currentWeight").value(71.0))
                .andExpect(jsonPath("$.data.changeKg").value(1.0))
                .andExpect(jsonPath("$.data.avgWeight").value(71.0))
                .andExpect(jsonPath("$.data.minWeight").value(70.0))
                .andExpect(jsonPath("$.data.maxWeight").value(72.0));
    }

    @Test
    @DisplayName("无记录时统计返回 0")
    void statsEmpty() throws Exception {
        MockHttpSession session = registerAndLogin("frank", "pass1234");

        mockMvc.perform(get("/api/weight/stats").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalRecords").value(0));
    }

    @Test
    @DisplayName("删除记录后统计归零")
    void deleteRecord() throws Exception {
        MockHttpSession session = registerAndLogin("grace", "pass1234");
        long id = addWeight(session, 70.0, "2026-11-11");

        mockMvc.perform(delete("/api/weight/" + id).session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        mockMvc.perform(get("/api/weight/stats").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalRecords").value(0));
    }

    @Test
    @DisplayName("不能删除他人记录（403）")
    void deleteOtherUserRecord() throws Exception {
        MockHttpSession alice = registerAndLogin("alice", "pass1234");
        long id = addWeight(alice, 70.0, "2026-11-11");

        MockHttpSession bob = registerAndLogin("bob", "pass1234");
        mockMvc.perform(delete("/api/weight/" + id).session(bob))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403));
    }

    /** 新增一条体重记录并返回其 id */
    private long addWeight(MockHttpSession session, double weightKg, String date) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/weight").session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(weightJson(weightKg, date)))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString())
                .path("data").path("id").asLong();
    }

    private String weightJson(double weightKg, String date) throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("weightKg", weightKg);
        body.put("recordDate", date);
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