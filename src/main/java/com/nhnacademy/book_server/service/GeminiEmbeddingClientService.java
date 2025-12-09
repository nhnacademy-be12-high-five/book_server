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

    private String endpoint() {
        // 정식 embedding endpoint
        return "https://generativelanguage.googleapis.com/v1beta/models/"
                + geminiConfig.getEmbeddingModel().replace("models/", "")
                + ":embedContents?key=" + geminiConfig.getApiKey();
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

        try {
            // 요청 Body = "requests" 배열
            StringBuilder sb = new StringBuilder();
            sb.append("{ \"model\": \"models/text-embedding-004\", \"requests\": [");

            for (int i = 0; i < texts.size(); i++) {
                sb.append("{ \"content\": { \"parts\": [ { \"text\": ")
                        .append(objectMapper.writeValueAsString(texts.get(i)))
                        .append(" } ] } }");
                if (i < texts.size() - 1) sb.append(",");
            }
            sb.append(" ] }");

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(endpoint()))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(sb.toString()))
                    .build();

            HttpResponse<String> response =
                    httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                log.error("Gemini embedding 실패 status={}, body={}",
                        response.statusCode(), response.body());
                return result;
            }

            JsonNode root = objectMapper.readTree(response.body());
            JsonNode embeddings = root.path("embeddings"); // 배열

            for (JsonNode emb : embeddings) {
                JsonNode values = emb.path("values");
                List<Float> vector = new ArrayList<>();
                for (JsonNode v : values) {
                    vector.add((float) v.asDouble());
                }
                result.add(vector);
            }

        } catch (Exception e) {
            log.error("Gemini embedding 예외", e);
        }

        return result;
    }
}
