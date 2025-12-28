package com.nhnacademy.book_server.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

@Configuration
public class RestTemplateConfig {

    // 1. 일반용 (기존 유지 + @Primary 추가)
    @Bean
    @Primary
    public RestTemplate restTemplate() {
        HttpComponentsClientHttpRequestFactory factory = new HttpComponentsClientHttpRequestFactory();
        factory.setConnectTimeout(3000); // 3초
        factory.setReadTimeout(5000);    // 5초
        return new RestTemplate(factory);
    }

    // 2. [추가] AI 전용 (5분 타임아웃)
    @Bean("ollamaRestTemplate")
    public RestTemplate ollamaRestTemplate() {
        HttpComponentsClientHttpRequestFactory factory = new HttpComponentsClientHttpRequestFactory();
        factory.setConnectTimeout(300000); // 5분
        factory.setReadTimeout(300000);    // 5분
        return new RestTemplate(factory);
    }
}