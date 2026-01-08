package com.nhnacademy.book_server.service.search;

import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

@Slf4j
@Service
@RequiredArgsConstructor
public class GeminiTextClientServiceImpl implements GeminiTextClientService {

    @Value("${gemini.api-key}")
    private String apiKey;

    private final RestTemplate restTemplate = new RestTemplate();

    /**
     *   AI 호출 폭주 방지 캐시
     * - 동일 prompt에 대한 성공 응답: 6일 캐시
     * - 429/403 등 실패 응답도: 15초 캐시(폭주 방지)
     */

    private static final long WARN_COOLDOWN_MS = 30_000L; // 30초
    private final AtomicLong lastWarnAt = new AtomicLong(0L);
    private final StringRedisTemplate redisTemplate;
    private static final long SUCCESS_TTL_SECONDS = 6L * 60 * 60; // 6시간
    private static final long FAIL_TTL_SECONDS = 30;            // 실패는 30초
    private static final String ERROR_MSG_NO_RESPONSE = "AI 추천 기능은 현재 응답을 생성하지 못했습니다. 잠시 후 다시 이용해 주세요.";

    @Override
    public String generateAnswer(String prompt) {
        //캐시 키 생성
        String key = normalizeKey(prompt);

        // Redis 캐시 먼저 확인
        String cached = redisTemplate.opsForValue().get(key);
        if (cached != null) {
            return cached;
        }

        String url = "https://generativelanguage.googleapis.com/v1beta/models/"
                + "gemini-2.5-flash:generateContent"
                + "?key=" + apiKey;

        try {
            GeminiRequest request = new GeminiRequest(
                    List.of(new Content(List.of(new Part(prompt))))
            );

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            HttpEntity<GeminiRequest> entity = new HttpEntity<>(request, headers);

            GeminiResponse response =
                    restTemplate.postForObject(url, entity, GeminiResponse.class);

            Optional<String> textOpt = extractTextFromResponse(response);

            if (textOpt.isEmpty()) {
                return cacheAndReturnError(key, ERROR_MSG_NO_RESPONSE, 30);
            }

            String text = textOpt.get();
            String answer = (text != null && !text.isBlank())
                    ? text
                    : ERROR_MSG_NO_RESPONSE;

            // Gemini 성공 응답 Redis 저장
            redisTemplate.opsForValue().set(key, answer, SUCCESS_TTL_SECONDS, TimeUnit.SECONDS);
            return answer;

        } catch (HttpClientErrorException.TooManyRequests e) { // 429
            handleTooManyRequestsLog();

            String msg =
                    "AI 추천 기능은 현재 요청량 제한으로 일시적으로 사용할 수 없습니다.\n"
                            + "도서 검색 및 목록 조회는 정상적으로 이용하실 수 있습니다.";
            return cacheAndReturnError(key, msg, FAIL_TTL_SECONDS);

        } catch (HttpClientErrorException.Forbidden e) { // 403
            return cacheAndReturnError(key, "AI 추천 기능 인증에 문제가 발생했습니다. 관리자에게 문의해 주세요.", FAIL_TTL_SECONDS);

        } catch (HttpClientErrorException e) { // 4xx 나머지
            return cacheAndReturnError(key, "AI 추천 기능 호출 중 오류가 발생했습니다. 잠시 후 다시 이용해 주세요.", FAIL_TTL_SECONDS);

        } catch (HttpServerErrorException.ServiceUnavailable e) { // 503
            return cacheAndReturnError(key, "AI 추천 기능이 일시적으로 불안정합니다. 잠시 후 다시 이용해 주세요.", FAIL_TTL_SECONDS);

        } catch (HttpServerErrorException e) { // 5xx 나머지
            return cacheAndReturnError(key, "AI 추천 기능 서버 오류가 발생했습니다. 잠시 후 다시 이용해 주세요.", FAIL_TTL_SECONDS);

        } catch (Exception e) { // 최후
            return cacheAndReturnError(key, "AI 추천 기능은 현재 제한으로 일시적으로 사용할 수 없습니다.", 30);
        }
    }

    // [Helper Method 1] 응답에서 텍스트 추출 (유효성 검사 포함)
    private Optional<String> extractTextFromResponse(GeminiResponse response) {
        if (response == null || response.getCandidates() == null || response.getCandidates().isEmpty()) {
            return Optional.empty();
        }
        Content c = response.getCandidates().get(0).getContent();
        if (c == null || c.getParts() == null || c.getParts().isEmpty()) {
            return Optional.empty();
        }
        // 구조가 유효하다면 텍스트 반환 (null일 수도 있음 - Optional.ofNullable로 처리)
        return Optional.ofNullable(c.getParts().get(0).getText());
    }

    // [Helper Method 2] 429 에러 로깅 처리
    private void handleTooManyRequestsLog() {
        long now = System.currentTimeMillis();
        long prev = lastWarnAt.get();

        if (now - prev >= WARN_COOLDOWN_MS && lastWarnAt.compareAndSet(prev, now)) {
            log.warn("Gemini 429 - 사용량 초과 (AI 기능 일시 중단, {}초 쿨다운 적용)", WARN_COOLDOWN_MS / 1000);
        }
    }

    // [Helper Method 3] 에러 메시지 캐싱 및 반환 (중복 코드 제거)
    private String cacheAndReturnError(String key, String message, long ttlSeconds) {
        redisTemplate.opsForValue().set(key, message, ttlSeconds, TimeUnit.SECONDS);
        return message;
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

    private String normalizeKey(String prompt) {
        if (prompt == null) return "";
        return prompt
                .trim()
                .replaceAll("\\s+", " ")
                .toLowerCase();
    }

}
