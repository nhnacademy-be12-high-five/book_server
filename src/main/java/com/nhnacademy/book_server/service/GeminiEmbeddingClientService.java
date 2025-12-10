package com.nhnacademy.book_server.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nhnacademy.book_server.config.GeminiConfig;
import com.nhnacademy.book_server.service.search.EmbeddingClientService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class GeminiEmbeddingClientService implements EmbeddingClientService {

    private final GeminiConfig geminiConfig;   // dim 등 필요하면 나중에 사용
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HttpClient httpClient = HttpClient.newHttpClient();

    @Value("${gemini.api-key}")
    private String apiKey;

    // 단건 embedContent 엔드포인트
    private String singleEndpoint() {
        return "https://generativelanguage.googleapis.com/v1beta/models/"
                + "text-embedding-004:embedContent"
                + "?key=" + apiKey;
    }

    @Override
    public List<Float> embed(String text) {
        List<Float> result = new ArrayList<>();
        if (text == null || text.isBlank()) {
            return result;
        }

        try {
            // 요청 바디
            String bodyJson = """
                    {
                      "model": "models/text-embedding-004",
                      "content": {
                        "parts": [
                          { "text": %s }
                        ]
                      }
                    }
                    """.formatted(objectMapper.writeValueAsString(text));

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(singleEndpoint()))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(bodyJson))
                    .build();

            HttpResponse<String> response =
                    httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                log.error("Gemini 단건 embedding 실패 status={}, body={}",
                        response.statusCode(), response.body());
                return result;
            }

            JsonNode root = objectMapper.readTree(response.body());
            // 응답 구조: { "embedding": { "values": [ ... ] } }
            JsonNode values = root.path("embedding").path("values");

            for (JsonNode v : values) {
                result.add((float) v.asDouble());
            }

        } catch (Exception e) {
            log.error("Gemini 단건 embedding 예외", e);
        }

        return result;
    }

    @Override
    public List<List<Float>> embedAll(List<String> texts) {
        List<List<Float>> result = new ArrayList<>();
        if (texts == null || texts.isEmpty()) {
            return result;
        }

        // 일단은 간단하게 “여러 번 embed() 호출”로 구현
        for (String text : texts) {
            List<Float> vec = embed(text);
            if (vec != null && !vec.isEmpty()) {
                result.add(vec);
            } else {
                // 실패한 건은 스킵
                log.warn("embedAll: 임베딩 실패, 해당 텍스트 건너뜀 text={}", text);
            }
        }

        return result;
    }
}
