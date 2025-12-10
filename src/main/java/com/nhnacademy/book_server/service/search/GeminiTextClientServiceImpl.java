package com.nhnacademy.book_server.service.search;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.List;

@Slf4j
@Service
public class GeminiTextClientServiceImpl implements GeminiTextClientService {

    // application.yml 에 설정해 둘 API 키
    @Value("${gemini.api-key}")
    private String apiKey;

    private final RestTemplate restTemplate = new RestTemplate();

    @Override
    public String generateAnswer(String prompt) {

        try {
            String url =
                    "https://generativelanguage.googleapis.com/v1/models/"
                            + "gemini-2.5-flash:generateContent"
                            + "?key=" + apiKey;


            // 요청 바디 만들기
            GeminiRequest request = new GeminiRequest(
                    List.of(new Content(
                            List.of(new Part(prompt))
                    ))
            );

            GeminiResponse response =
                    restTemplate.postForObject(url, request, GeminiResponse.class);

            if (response == null ||
                    response.getCandidates() == null ||
                    response.getCandidates().isEmpty()) {
                log.warn("Gemini 응답이 비어 있습니다.");
                return "현재 질문에 대한 AI 응답을 생성하지 못했습니다.";
            }

            Content content = response.getCandidates().get(0).getContent();
            if (content == null ||
                    content.getParts() == null ||
                    content.getParts().isEmpty()) {
                log.warn("Gemini 응답에 content/parts 가 없습니다.");
                return "현재 질문에 대한 AI 응답을 생성하지 못했습니다.";
            }

            String text = content.getParts().get(0).getText();
            return (text != null && !text.isBlank())
                    ? text
                    : "현재 질문에 대한 AI 응답을 생성하지 못했습니다.";

        } catch (Exception e) {
            log.error("Gemini 텍스트 생성 호출 중 오류", e);
            return "AI 응답 생성 중 오류가 발생했습니다.";
        }
    }

    // ================== 이하 요청/응답 DTO ==================

    @Data
    public static class GeminiRequest {
        private List<Content> contents;

        public GeminiRequest(List<Content> contents) {
            this.contents = contents;
        }
    }

    @Data
    public static class Content {
        private List<Part> parts;

        public Content(List<Part> parts) {
            this.parts = parts;
        }
    }

    @Data
    public static class Part {
        private String text;

        public Part(String text) {
            this.text = text;
        }
    }

    @Data
    public static class GeminiResponse {
        private List<Candidate> candidates;
    }

    @Data
    public static class Candidate {
        private Content content;
    }
}
