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

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@Slf4j
@Service
public class GeminiTextClientServiceImpl implements GeminiTextClientService {

    @Value("${gemini.api-key}")
    private String apiKey;

    private final RestTemplate restTemplate = new RestTemplate();

    /**
     *   AI 호출 폭주 방지 캐시
     * - 동일 prompt에 대한 성공 응답: 30초 캐시
     * - 429/403 등 실패 응답도: 15초 캐시(폭주 방지)
     */
    private static final Duration SUCCESS_TTL = Duration.ofSeconds(30);
    private static final Duration FAIL_TTL = Duration.ofSeconds(15);
    private static final long WARN_COOLDOWN_MS = 30_000L; // 30초
    private final AtomicLong lastWarnAt = new AtomicLong(0L);

    private final Map<String, CacheEntry> answerCache = new ConcurrentHashMap<>();

    private static class CacheEntry {
        final String value;
        final long expiresAtMillis;

        CacheEntry(String value, long expiresAtMillis) {
            this.value = value;
            this.expiresAtMillis = expiresAtMillis;
        }

        boolean isExpired() {
            return System.currentTimeMillis() > expiresAtMillis;
        }
    }

    @Override
    public String generateAnswer(String prompt) {
        // 1) 캐시 먼저 확인 (API 호출 차단)
        CacheEntry cached = answerCache.get(prompt);
        if (cached != null) {
            if (!cached.isExpired()) {
                return cached.value;
            }
            answerCache.remove(prompt);
        }

        String url =
                "https://generativelanguage.googleapis.com/v1/models/"
                        + "gemini-1.5-flash:generateContent"
                        + "?key=" + apiKey;

        try {
            GeminiRequest request = new GeminiRequest(
                    List.of(new Content(
                            List.of(new Part(prompt))
                    ))
            );

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
                String msg = "AI 추천 기능은 현재 응답을 생성하지 못했습니다. 잠시 후 다시 이용해 주세요.";
                answerCache.put(prompt, new CacheEntry(msg,
                        System.currentTimeMillis() + FAIL_TTL.toMillis()));
                return msg;
            }

            Content c = response.getCandidates().get(0).getContent();
            if (c == null || c.getParts() == null || c.getParts().isEmpty()) {
                log.warn("Gemini 응답에 content/parts 없음.");
                String msg = "AI 추천 기능은 현재 응답을 생성하지 못했습니다. 잠시 후 다시 이용해 주세요.";
                answerCache.put(prompt, new CacheEntry(msg,
                        System.currentTimeMillis() + FAIL_TTL.toMillis()));
                return msg;
            }

            String text = c.getParts().get(0).getText();
            String answer = (text != null && !text.isBlank())
                    ? text
                    : "AI 추천 기능은 현재 응답을 생성하지 못했습니다. 잠시 후 다시 이용해 주세요.";

            // 2) 성공 응답 캐시 저장
            answerCache.put(prompt, new CacheEntry(answer,
                    System.currentTimeMillis() + SUCCESS_TTL.toMillis()));

            return answer;
        }

        // ------------------------ //
        //        ★ 429 처리        //
        // ------------------------ //
        catch (HttpClientErrorException.TooManyRequests e) {
            long now = System.currentTimeMillis();
            long prev = lastWarnAt.get();

            if (now - prev >= WARN_COOLDOWN_MS && lastWarnAt.compareAndSet(prev, now)) {
                log.warn("Gemini 429 - 사용량 초과 (AI 기능 일시 중단, {}초 쿨다운 적용)", WARN_COOLDOWN_MS / 1000);
            }

            String msg =
                    "AI 추천 기능은 현재 요청량 제한으로 일시적으로 사용할 수 없습니다.\n"
                            + "도서 검색 및 목록 조회는 정상적으로 이용하실 수 있습니다.";

            // 실패도 캐시 저장하고 있다면 그대로 유지
            answerCache.put(prompt, new CacheEntry(msg,
                    System.currentTimeMillis() + FAIL_TTL.toMillis()));

            return msg;
        }


        // ------------------------ //
        //        ★ 403 처리        //
        // ------------------------ //
        catch (HttpClientErrorException.Forbidden e) {
            log.warn("Gemini 403 - 권한 거부(키 문제 가능). 메시지={}", e.getMessage());
            String msg =
                    "AI 추천 기능 설정 문제로 현재 사용할 수 없습니다.\n"
                            + "(관리자: API 키 상태 확인 필요)";
            answerCache.put(prompt, new CacheEntry(msg,
                    System.currentTimeMillis() + FAIL_TTL.toMillis()));
            return msg;
        }

        // ------------------------ //
        //        ★ 기타 4xx        //
        // ------------------------ //
        catch (HttpClientErrorException e) {
            // 4xx는 운영에서 종종 발생하므로 stacktrace 폭주 방지
            log.warn("Gemini 4xx 오류: status={}, message={}", e.getStatusCode(), e.getMessage());
            String msg =
                    "AI 추천 기능을 일시적으로 사용할 수 없습니다.\n"
                            + "도서 검색 및 목록 조회는 정상적으로 이용하실 수 있습니다.";
            answerCache.put(prompt, new CacheEntry(msg,
                    System.currentTimeMillis() + FAIL_TTL.toMillis()));
            return msg;
        }

        // ------------------------ //
        //        ★ 503 처리        //
        // ------------------------ //
        catch (HttpServerErrorException.ServiceUnavailable e) {
            log.warn("Gemini 503 - 모델 과부하(일시적 혼잡)");
            String msg = "AI 서버가 현재 혼잡합니다. 잠시 후 다시 시도해 주세요.";
            answerCache.put(prompt, new CacheEntry(msg,
                    System.currentTimeMillis() + FAIL_TTL.toMillis()));
            return msg;
        }

        // ------------------------ //
        //        ★ 기타 5xx        //
        // ------------------------ //
        catch (HttpServerErrorException e) {
            log.warn("Gemini 서버 오류: status={}, message={}", e.getStatusCode(), e.getMessage());
            String msg = "AI 추천 기능 서버에 오류가 발생했습니다. 잠시 후 다시 시도해 주세요.";
            answerCache.put(prompt, new CacheEntry(msg,
                    System.currentTimeMillis() + FAIL_TTL.toMillis()));
            return msg;
        }

        // ------------------------ //
        //       ★ 기타 예외        //
        // ------------------------ //
        catch (Exception e) {
            log.warn("Gemini 호출 중 알 수 없는 오류: {}", e.getMessage());
            String msg = "AI 응답 생성 중 오류가 발생했습니다. 잠시 후 다시 시도해주세요.";
            answerCache.put(prompt, new CacheEntry(msg,
                    System.currentTimeMillis() + FAIL_TTL.toMillis()));
            return msg;
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
