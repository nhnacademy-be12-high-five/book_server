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
            } catch (Exception e) {
                if (i == maxRetries - 1) {
                    log.error("Ollama 임베딩 최종 실패 (text length={}): {}", text.length(), e.getMessage());
                } else {
                    log.warn("Ollama 응답 지연, 재시도 중... ({}/{})", i + 1, maxRetries);
                    try { Thread.sleep(1000); } catch (InterruptedException ignored) {}
                }
            }
        }
        return Collections.emptyList();
    }

    private record OllamaRequest(String model, String prompt) {}
    private record OllamaResponse(@JsonProperty("embedding") List<Float> embedding) {}
}