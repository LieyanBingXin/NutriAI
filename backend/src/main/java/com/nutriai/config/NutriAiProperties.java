package com.nutriai.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.List;

@Data
@Configuration
@ConfigurationProperties(prefix = "nutriai")
public class NutriAiProperties {

    private Ai ai = new Ai();
    private Mail mail = new Mail();
    private Cors cors = new Cors();
    private DemoData demoData = new DemoData();

    @Data
    public static class Ai {
        private String provider = "mock";
        private String apiKey = "";
        private String apiEndpoint = "";
        private String model = "";
    }

    @Data
    public static class Mail {
        private String from = "no-reply@nutriai.local";
        private String baseUrl = "http://localhost:8080";
    }

    @Data
    public static class Cors {
        /** 允许携带凭据跨域访问的前端来源；为空表示仅同源访问。 */
        private List<String> allowedOrigins = new ArrayList<>();
    }

    @Data
    public static class DemoData {
        /** dev 环境是否自动创建演示账号和示例数据。 */
        private boolean enabled = true;
    }
}
