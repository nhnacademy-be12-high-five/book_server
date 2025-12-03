package com.nhnacademy.book_server.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nhnacademy.book_server.config.GeminiConfig;
import com.nhnacademy.book_server.service.search.EmbeddingClientService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
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

    private final GeminiConfig geminiConfig;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HttpClient httpClient = HttpClient.newHttpClient();

    // 실제 호출할 엔드포인트
    private String endpoint() {
        // https://generativelanguage.googleapis.com/v1beta/models/text-embedding-004:embedContent?key=...
        String modelName = geminiConfig.getEmbeddingModel().replace("models/", "");
        return "https://generativelanguage.googleapis.com/v1beta/models/"
                + modelName
                + ":embedContent?key="
                + geminiConfig.getApiKey();
    }

    @Override
    public List<Float> embed(String text) {
        List<List<Float>> list = embedAll(List.of(text));
        return list.isEmpty() ? List.of() : list.get(0);
    }

    @Override
    public List<List<Float>> embedAll(List<String> texts) {
        List<List<Float>> result = new ArrayList<>();

        if (texts == null || texts.isEmpty()) {
            return result;
        }

        for (String text : texts) {
            try {
                // 요청 Body JSON
                String bodyJson = """
                        {
                          "model": "%s",
                          "content": {
                            "parts": [
                              { "text": %s }
                            ]
                          }
                        }
                        """.formatted(
                        geminiConfig.getEmbeddingModel(),
                        objectMapper.writeValueAsString(text)
                );

                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(endpoint()))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(bodyJson))
                        .build();

                HttpResponse<String> response =
                        httpClient.send(request, HttpResponse.BodyHandlers.ofString());

                if (response.statusCode() != 200) {
                    log.error("Gemini embedding 실패 status={}, body={}",
                            response.statusCode(), response.body());
                    continue;
                }

                JsonNode root = objectMapper.readTree(response.body());
                JsonNode values = root.path("embedding").path("values");

                List<Float> vector = new ArrayList<>();
                for (JsonNode v : values) {
                    vector.add((float) v.asDouble());
                }
                result.add(vector);

            } catch (IOException | InterruptedException e) {
                log.error("Gemini embedding 예외", e);
                Thread.currentThread().interrupt();
            }
        }

        return result;
    }
}
