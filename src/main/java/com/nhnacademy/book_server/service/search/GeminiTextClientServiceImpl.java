package com.nhnacademy.book_server.service.search;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestTemplate;

import java.util.List;

@Slf4j
@Service
public class GeminiTextClientServiceImpl implements GeminiTextClientService {

    @Value("${gemini.api-key}")
    private String apiKey;

    private final RestTemplate restTemplate = new RestTemplate();

    @Override
    public String generateAnswer(String prompt) {

        String url =
                "https://generativelanguage.googleapis.com/v1/models/"
                        + "gemini-1.5-flash:generateContent"
                        + "?key=" + apiKey;

        try {
            // 요청 바디
            GeminiRequest request = new GeminiRequest(
                    List.of(new Content(
                            List.of(new Part(prompt))
                    ))
            );

            // JSON 헤더
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            HttpEntity<GeminiRequest> entity = new HttpEntity<>(request, headers);

            GeminiResponse response =
                    restTemplate.postForObject(url, entity, GeminiResponse.class);

            // 응답 검증
            if (response == null ||
                    response.getCandidates() == null ||
                    response.getCandidates().isEmpty()) {
                log.warn("Gemini 응답이 비어 있음.");
                return "AI 추천 설명을 가져오지 못했습니다.";
            }

            Content c = response.getCandidates().get(0).getContent();
            if (c == null || c.getParts() == null || c.getParts().isEmpty()) {
                log.warn("Gemini 응답에 content/parts 없음.");
                return "AI 추천 설명을 가져오지 못했습니다.";
            }

            String text = c.getParts().get(0).getText();
            return (text != null && !text.isBlank())
                    ? text
                    : "AI 추천 설명을 가져오지 못했습니다.";

        }
        // ------------------------ //
        //        ★ 429 처리        //
        // ------------------------ //
        catch (HttpClientErrorException.TooManyRequests e) {
            log.error("Gemini 429 - 사용량 초과됨", e);
            return "오늘 제공되는 AI 추천 사용량이 모두 소진되었습니다. "
                    + "잠시 후 다시 이용해 주세요.";
        }

        // ------------------------ //
        //        ★ 4xx 오류        //
        // ------------------------ //
        catch (HttpClientErrorException e) {
            log.error("Gemini 4xx 오류", e);
            return "AI 추천 기능을 일시적으로 사용할 수 없습니다.";
        }

        // ------------------------ //
        //        ★ 503 처리        //
        // ------------------------ //
        catch (HttpServerErrorException.ServiceUnavailable e) {
            log.error("Gemini 503 - 모델 과부하", e);
            return "AI 서버가 현재 혼잡합니다. 잠시 후 다시 시도해 주세요.";
        }

        // ------------------------ //
        //        ★ 기타 5xx        //
        // ------------------------ //
        catch (HttpServerErrorException e) {
            log.error("Gemini 서버 오류", e);
            return "AI 추천 기능 서버에 오류가 발생했습니다.";
        }

        // ------------------------ //
        //       ★ 나머지 모든 오류 //
        // ------------------------ //
        catch (Exception e) {
            log.error("Gemini 호출 중 알 수 없는 오류", e);
            return "AI 응답 생성 중 오류가 발생했습니다. 잠시 후 다시 시도해주세요.";
        }
    }

    @Override
    public String getReviewSummary(String bookTitle, List<String> reviews) {
        // 프롬프트 엔지니어링: 역할 부여 및 포맷 지정
        StringBuilder sb = new StringBuilder();
        sb.append("너는 서점의 전문 북 큐레이터야. 다음은 '").append(bookTitle).append("' 책에 대한 최근 독자들의 리뷰야.\n");
        sb.append("이 리뷰들을 분석해서 장점, 단점, 그리고 한줄 요약을 해줘.\n\n");
        sb.append("--- 리뷰 리스트 ---\n");

        for (String review : reviews) {
            if(review.length() > 5) {
                sb.append("- ").append(review.replace("\n", " ")).append("\n");
            }
        }

        sb.append("\n--- 요청 사항 ---\n");
        sb.append("1. 장점: 독자들이 공통적으로 칭찬하는 부분\n");
        sb.append("2. 단점: 독자들이 아쉬워하는 부분\n");
        sb.append("3. 한줄평: 전체적인 분위기를 요약\n");
        sb.append("한국어로 자연스럽게 작성해주고, 적절한 이모지를 사용해줘.");

        return generateAnswer(sb.toString());
    }

    /* ====== 요청/응답 DTO ====== */

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
