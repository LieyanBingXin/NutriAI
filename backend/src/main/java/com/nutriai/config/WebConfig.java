package com.nutriai.config;

import java.time.Duration;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.springframework.web.reactive.function.client.WebClient;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import reactor.netty.http.client.HttpClient;

/**
 * 通用 Web 配置。
 * <p>注意：CORS 统一由 {@link SecurityConfig#corsConfigurationSource()} 管理，
 * 这里不再重复注册 CorsFilter（双重配置会让允许来源规则互相覆盖）。
 */
@Configuration
public class WebConfig {

    @Bean
    @Primary
    public ObjectMapper objectMapper(Jackson2ObjectMapperBuilder builder) {
        // 复用 Spring Boot 的 builder（自动注册 JavaTime / Jdk8 / ParameterNames 等模块），
        // 再叠加项目需要的宽容解析策略。
        return builder
                .build()
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
                .configure(DeserializationFeature.ACCEPT_SINGLE_VALUE_AS_ARRAY, true)
                .configure(DeserializationFeature.UNWRAP_SINGLE_VALUE_ARRAYS, true)
                .configure(com.fasterxml.jackson.core.JsonParser.Feature.ALLOW_TRAILING_COMMA, true)
                .configure(com.fasterxml.jackson.core.JsonParser.Feature.ALLOW_COMMENTS, true);
    }

    @Bean
    public WebClient.Builder webClientBuilder() {
        HttpClient httpClient = HttpClient.create()
                .responseTimeout(Duration.ofSeconds(90))
                .option(io.netty.channel.ChannelOption.CONNECT_TIMEOUT_MILLIS, 10_000)
                .keepAlive(true);
        return WebClient.builder()
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .exchangeStrategies(ExchangeStrategies.builder()
                        .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(16 * 1024 * 1024))
                        .build());
    }
}