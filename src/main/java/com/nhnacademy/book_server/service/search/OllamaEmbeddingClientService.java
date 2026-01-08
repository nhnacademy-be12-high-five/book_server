package com.nhnacademy.book_server.service.search;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.Collections;
import java.util.List;

@Slf4j
@Service
@Primary
public class OllamaEmbeddingClientService implements EmbeddingClientService {

    private static final String OLLAMA_API_URL = "http://ollama.java21.net/api/embeddings";
    private final RestTemplate restTemplate;

    // [중요] 생성자에서 "ollamaRestTemplate"을 주입받도록 명시
    public OllamaEmbeddingClientService(@Qualifier("ollamaRestTemplate") RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    @Override
    public List<Float> embed(String text) {
        if (text == null || text.isBlank()) {
            return Collections.emptyList();
        }

        // 3회 재시도 로직 추가
        int maxRetries = 3;
        for (int i = 0; i < maxRetries; i++) {
            try {
                List<Float> result = fetchEmbedding(text);
                if (!result.isEmpty()) {
                    return result;
                }
            } catch (Exception e) {
                handleException(i, maxRetries, text, e);
            }
        }
        return Collections.emptyList();
    }

    private record OllamaRequest(String model, String prompt) {}
    private record OllamaResponse(@JsonProperty("embedding") List<Float> embedding) {}

    // Helper Method: API 호출 수행
    private List<Float> fetchEmbedding(String text) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        OllamaRequest request = new OllamaRequest("bge-m3", text);
        HttpEntity<OllamaRequest> entity = new HttpEntity<>(request, headers);

        OllamaResponse response = restTemplate.postForObject(
                OLLAMA_API_URL,
                entity,
                OllamaResponse.class
        );

        if (response != null && response.embedding() != null) {
            return response.embedding();
        }
        return Collections.emptyList();
    }

    // Helper Method: 예외 로깅 및 재시도 대기
    private void handleException(int currentAttempt, int maxRetries, String text, Exception e) {
        if (currentAttempt == maxRetries - 1) {
            log.error("Ollama 임베딩 최종 실패 (text length={}): {}", text.length(), e.getMessage());
        } else {
            log.warn("Ollama 응답 지연, 재시도 중... ({}/{})", currentAttempt + 1, maxRetries);
            try {
                Thread.sleep(1000);
            } catch (InterruptedException ie) {
                // [수정 3] InterruptedException 발생 시 인터럽트 상태 복구 및 로그 추가 (SonarQube 규칙 준수)
                Thread.currentThread().interrupt();
                log.warn("Ollama 재시도 대기 중 인터럽트 발생");
            }
        }
    }
}