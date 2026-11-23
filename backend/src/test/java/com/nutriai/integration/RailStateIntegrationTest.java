package com.nutriai.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nutriai.repository.RailStateRepository;
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

import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 便利贴 rail 跨设备同步的集成测试。
 * 用 H2（MySQL 兼容模式）代替本地 MySQL，不依赖外部服务即可跑完整链路。
 * 覆盖：认证拦截、首次空结构、保存读回、结构归一化、跨会话同步。
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:nutriai_test;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "nutriai.demo-data.enabled=false"
})
@AutoConfigureMockMvc
class RailStateIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RailStateRepository railStateRepository;

    @BeforeEach
    void clean() {
        railStateRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    @DisplayName("未登录访问 rail 返回 401")
    void railRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/assistant/rail"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(401));
    }

    @Test
    @DisplayName("登录后初始 rail 为空五键结构")
    void initialRailIsEmpty() throws Exception {
        MockHttpSession session = registerAndLogin("alice", "pass1234");

        mockMvc.perform(get("/api/assistant/rail").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.likes").isArray())
                .andExpect(jsonPath("$.data.likes").isEmpty())
                .andExpect(jsonPath("$.data.dislikes").isEmpty())
                .andExpect(jsonPath("$.data.fridge").isEmpty())
                .andExpect(jsonPath("$.data.gap").isEmpty())
                .andExpect(jsonPath("$.data.custom").isEmpty());
    }

    @Test
    @DisplayName("保存后读回完整数据")
    void saveThenLoad() throws Exception {
        MockHttpSession session = registerAndLogin("bob", "pass1234");
        String rail = "{\"likes\":[\"chicken\"],\"dislikes\":[\"coriander\"],\"fridge\":[\"egg\"],\"gap\":[\"protein\"],\"custom\":[\"drink water\"]}";

        mockMvc.perform(put("/api/assistant/rail").session(session)
                        .contentType(MediaType.APPLICATION_JSON).content(rail))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value("ok"));

        mockMvc.perform(get("/api/assistant/rail").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.likes[0]").value("chicken"))
                .andExpect(jsonPath("$.data.dislikes[0]").value("coriander"))
                .andExpect(jsonPath("$.data.fridge[0]").value("egg"))
                .andExpect(jsonPath("$.data.gap[0]").value("protein"))
                .andExpect(jsonPath("$.data.custom[0]").value("drink water"));
    }

    @Test
    @DisplayName("结构归一化：非法键丢弃、非数组值清空")
    void normalizeDropsInvalidKeys() throws Exception {
        MockHttpSession session = registerAndLogin("carol", "pass1234");
        String rail = "{\"likes\":[\"ok\"],\"dislikes\":\"not-an-array\",\"fridge\":[\"egg\"],\"extra\":[\"drop-me\"],\"custom\":123}";

        mockMvc.perform(put("/api/assistant/rail").session(session)
                        .contentType(MediaType.APPLICATION_JSON).content(rail))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/assistant/rail").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.likes[0]").value("ok"))
                .andExpect(jsonPath("$.data.dislikes").isEmpty())
                .andExpect(jsonPath("$.data.fridge[0]").value("egg"))
                .andExpect(jsonPath("$.data.custom").isEmpty())
                .andExpect(jsonPath("$.data.extra").doesNotExist());
    }

    @Test
    @DisplayName("跨会话重新登录读到已保存数据（换设备同步）")
    void syncAcrossSessions() throws Exception {
        MockHttpSession first = registerAndLogin("dave", "pass1234");
        String rail = "{\"likes\":[\"apple\"],\"dislikes\":[],\"fridge\":[\"milk\"],\"gap\":[],\"custom\":[]}";

        mockMvc.perform(put("/api/assistant/rail").session(first)
                        .contentType(MediaType.APPLICATION_JSON).content(rail))
                .andExpect(status().isOk());

        // 全新 session 重新登录，模拟换设备
        MvcResult login = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(authJson("dave", "pass1234")))
                .andExpect(status().isOk())
                .andReturn();
        MockHttpSession second = (MockHttpSession) login.getRequest().getSession(false);

        mockMvc.perform(get("/api/assistant/rail").session(second))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.likes[0]").value("apple"))
                .andExpect(jsonPath("$.data.fridge[0]").value("milk"));
    }

    /** 注册（不传 email，即邮箱已验证）并登录，返回已认证的 session */
    private MockHttpSession registerAndLogin(String username, String password) throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(authJson(username, password)))
                .andExpect(status().isOk());

        MvcResult login = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(authJson(username, password)))
                .andExpect(status().isOk())
                .andReturn();

        return (MockHttpSession) login.getRequest().getSession(false);
    }

    private String authJson(String username, String password) throws Exception {
        return objectMapper.writeValueAsString(Map.of("username", username, "password", password));
    }
}