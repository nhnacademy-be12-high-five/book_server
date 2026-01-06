package com.nhnacademy.book_server.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;

@Slf4j
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    // "java:S4502"는 CSRF 보호 비활성화에 대한 SonarQube 규칙 ID입니다.
    // REST API 환경이므로 CSRF가 불필요함을 명시하고 경고를 억제합니다.
    @SuppressWarnings("java:S4502")
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        log.info(">>> BOOK-SERVER SecurityConfig LOADED");
        http
                .csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(
                                "/actuator/**",
                                "/api/books/**",
                                "/api/categories/**",
                                "/api/search/**",
                                "/actuator/**",
                                "/api/tag/**",
                                "/api/tags/**",
                                "/api/admin/**",
                                "/api/admin/books/search-api",
                                "/api/my-page/**"
                        ).permitAll()
                        .requestMatchers("/api/test/**").permitAll()
                        .anyRequest().authenticated()
                );

        return http.build();
    }

}