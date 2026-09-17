package com.nutriai.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "nutriai")
public class NutriAiProperties {

    private Ai ai = new Ai();

    @Data
    public static class Ai {
        private String provider = "mock";
        private String apiKey = "";
        private String apiEndpoint = "";
        private String model = "";
    }
}