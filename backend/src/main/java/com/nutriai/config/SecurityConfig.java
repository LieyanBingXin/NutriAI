package com.nutriai.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nutriai.service.CustomUserDetailsService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final CustomUserDetailsService userDetailsService;
    private final ObjectMapper objectMapper;
    private final NutriAiProperties properties;

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider();
        provider.setUserDetailsService(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder());
        return provider;
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }

    @Bean
    public SecurityContextRepository securityContextRepository() {
        return new HttpSessionSecurityContextRepository();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        List<String> origins = properties.getCors().getAllowedOrigins();
        if (origins != null && !origins.isEmpty()) {
            config.setAllowedOrigins(origins);
        } else {
            // 未显式配置时仅允许同源访问（同源请求不经过 CORS 校验）。
            config.setAllowedOrigins(List.of());
        }
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setExposedHeaders(List.of("Content-Disposition"));
        config.setAllowCredentials(true);
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.ALWAYS)
                        .sessionFixation(fixation -> fixation.migrateSession())
                        .maximumSessions(1)
                )
                .headers(headers -> headers
                        .frameOptions(frame -> frame.deny())
                        .contentTypeOptions(contentType -> {})
                        .referrerPolicy(referrer -> referrer.policy(
                                org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter.ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN))
                )
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint((request, response, authException) ->
                                writeJson(response, HttpStatus.UNAUTHORIZED, 401, "未登录"))
                        .accessDeniedHandler((request, response, accessDeniedException) ->
                                writeJson(response, HttpStatus.FORBIDDEN, 403, "无权访问"))
                )
                .authorizeHttpRequests(auth -> {
                    auth.requestMatchers(
                            "/",
                            "/index.html",
                            "/css/**",
                            "/js/**",
                            "/vendor/**",
                            "/images/**",
                            "/favicon.ico",
                            "/favicon.svg",
                            "/manifest.webmanifest",
                            "/manifest.json",
                            "/sw.js",
                            "/offline.html",
                            "/icons/**",
                            "/error",
                            "/api/auth/register",
                            "/api/auth/login",
                            "/api/auth/logout",
                            "/api/auth/me",
                            "/api/auth/verify",
                            "/api/auth/resend-verify",
                            "/api/auth/forgot-password",
                            "/api/auth/reset-password",
                            "/reset-password",
                            "/reset-password.html",
                            "/api/health",
                            "/api/recommend",
                            "/api/recommend/stream",
                            "/api/recommend/goals"
                    ).permitAll();
                    auth.anyRequest().authenticated();
                })
                .authenticationProvider(authenticationProvider())
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable);

        return http.build();
    }

    private void writeJson(jakarta.servlet.http.HttpServletResponse response, HttpStatus status,
                           int code, String message) throws java.io.IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        Map<String, Object> body = new HashMap<>();
        body.put("code", code);
        body.put("message", message);
        body.put("data", null);
        body.put("timestamp", System.currentTimeMillis());
        response.getWriter().write(objectMapper.writeValueAsString(body));
    }
}