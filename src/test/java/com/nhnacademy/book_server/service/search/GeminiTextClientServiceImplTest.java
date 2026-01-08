package com.nhnacademy.book_server.service.search;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;


@SpringBootTest(
        classes = GeminiTextClientServiceImpl.class,
        properties = {
                "gemini.api-key=dummy_test_key"
        }
)
class GeminiTextClientServiceImplTest {

    @Autowired
    GeminiTextClientServiceImpl service;

    @MockitoBean
    StringRedisTemplate redisTemplate;

    @MockitoBean
    ValueOperations<String, String> valueOperations;

    MockRestServiceServer server;
    RestTemplate restTemplate;

    private static final String BASE_URL =
            "https://generativelanguage.googleapis.com/v1beta/models/"
                    + "gemini-2.5-flash:generateContent?key=dummy_test_key";

    private static final String DEFAULT_EMPTY_MSG =
            "AI 추천 기능은 현재 응답을 생성하지 못했습니다. 잠시 후 다시 이용해 주세요.";

    private static final String MSG_429 =
            "AI 추천 기능은 현재 요청량 제한으로 일시적으로 사용할 수 없습니다.\n"
                    + "도서 검색 및 목록 조회는 정상적으로 이용하실 수 있습니다.";

    private static final String MSG_403 =
            "AI 추천 기능 인증에 문제가 발생했습니다. 관리자에게 문의해 주세요.";

    private static final String MSG_503 =
            "AI 추천 기능이 일시적으로 불안정합니다. 잠시 후 다시 이용해 주세요.";

    private static final String MSG_4XX =
            "AI 추천 기능 호출 중 오류가 발생했습니다. 잠시 후 다시 이용해 주세요.";

    private static final String MSG_5XX =
            "AI 추천 기능 서버 오류가 발생했습니다. 잠시 후 다시 이용해 주세요.";

    @BeforeEach
    void setUp() {
        // Redis opsForValue() 기본 스텁
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        // 서비스 내부 필드 restTemplate( new RestTemplate() )를 꺼내 MockRestServiceServer 연결
        restTemplate = (RestTemplate) ReflectionTestUtils.getField(service, "restTemplate");
        server = MockRestServiceServer.createServer(restTemplate);
    }

    // ------------------------
    // 캐시 hit / normalizeKey
    // ------------------------

    @Test
    @DisplayName("generateAnswer: Redis 캐시 hit이면 즉시 반환하고 외부 호출하지 않는다 (normalizeKey 포함)")
    void generateAnswer_cacheHit_returnsCached_andNoHttpCall() {
        String prompt = "   Hello     WORLD   ";
        // normalizeKey 결과는 "hello world"
        when(valueOperations.get("hello world")).thenReturn("CACHED_VALUE");

        String result = service.generateAnswer(prompt);

        assertThat(result).isEqualTo("CACHED_VALUE");

        // HTTP 기대 없음(서버 검증만)
        server.verify();

        verify(valueOperations, times(1)).get("hello world");
        verify(valueOperations, never()).set(anyString(), anyString(), anyLong(), any(TimeUnit.class));
    }

    // ------------------------
    // 정상 응답 파싱 / TTL
    // ------------------------

    @Test
    @DisplayName("generateAnswer: cache miss + 정상 Gemini 응답이면 text 반환 + 6시간 캐시 저장")
    void generateAnswer_success_caches6Hours() {
        when(valueOperations.get(anyString())).thenReturn(null);

        server.expect(once(), requestTo(BASE_URL))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess(successJson("AI_TEXT"), MediaType.APPLICATION_JSON));

        String result = service.generateAnswer("some prompt");

        assertThat(result).isEqualTo("AI_TEXT");

        // TTL 6 hours 저장 검증 (코드: set(key, answer, 6, TimeUnit.HOURS))
        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> valCaptor = ArgumentCaptor.forClass(String.class);

        verify(valueOperations).set(keyCaptor.capture(), valCaptor.capture(), eq(21600L), eq(TimeUnit.SECONDS));

        // normalizeKey 적용 확인(소문자/공백 정리)
        assertThat(keyCaptor.getValue()).isEqualTo("some prompt");
        assertThat(valCaptor.getValue()).isEqualTo("AI_TEXT");

        server.verify();
    }

    @Test
    @DisplayName("generateAnswer: text가 blank면 DEFAULT_EMPTY_MSG 반환 + 6시간 캐시 저장")
    void generateAnswer_blankText_returnsDefault_andCaches6Hours() {
        when(valueOperations.get(anyString())).thenReturn(null);

        server.expect(once(), requestTo(BASE_URL))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess(successJson("   "), MediaType.APPLICATION_JSON));

        String result = service.generateAnswer("PROMPT");

        assertThat(result).isEqualTo(DEFAULT_EMPTY_MSG);
        verify(valueOperations).set("prompt", DEFAULT_EMPTY_MSG, 21600L, TimeUnit.SECONDS);

        server.verify();
    }

    // ------------------------
    // 응답 비정상(빈 candidates / 빈 parts / null 등) -> 30초 캐시
    // ------------------------

    @Test
    @DisplayName("generateAnswer: response 자체가 null이면 DEFAULT_EMPTY_MSG + 30초 캐시")
    void generateAnswer_responseNull_cache30s() {
        when(valueOperations.get(anyString())).thenReturn(null);

        server.expect(once(), requestTo(BASE_URL))
                .andExpect(method(HttpMethod.POST))
                // body = "" 이면 Jackson이 GeminiResponse null로 만들 가능성이 있어 명시적으로 200 + 빈 JSON으로 처리
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

        String result = service.generateAnswer("A");

        // 이 경우: candidates==null -> DEFAULT_EMPTY_MSG + 30초 캐시
        assertThat(result).isEqualTo(DEFAULT_EMPTY_MSG);
        verify(valueOperations).set("a", DEFAULT_EMPTY_MSG, 30L, TimeUnit.SECONDS);

        server.verify();
    }

    @Test
    @DisplayName("generateAnswer: candidates가 empty면 DEFAULT_EMPTY_MSG + 30초 캐시")
    void generateAnswer_candidatesEmpty_cache30s() {
        when(valueOperations.get(anyString())).thenReturn(null);

        String emptyCandidates = "{ \"candidates\": [] }";

        server.expect(once(), requestTo(BASE_URL))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess(emptyCandidates, MediaType.APPLICATION_JSON));

        String result = service.generateAnswer("B");

        assertThat(result).isEqualTo(DEFAULT_EMPTY_MSG);
        verify(valueOperations).set("b", DEFAULT_EMPTY_MSG, 30L, TimeUnit.SECONDS);

        server.verify();
    }

    @Test
    @DisplayName("generateAnswer: content.parts가 empty면 DEFAULT_EMPTY_MSG + 30초 캐시")
    void generateAnswer_partsEmpty_cache30s() {
        when(valueOperations.get(anyString())).thenReturn(null);

        String partsEmpty = """
                {
                  "candidates": [
                    { "content": { "parts": [] } }
                  ]
                }
                """;

        server.expect(once(), requestTo(BASE_URL))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess(partsEmpty, MediaType.APPLICATION_JSON));

        String result = service.generateAnswer("C");

        assertThat(result).isEqualTo(DEFAULT_EMPTY_MSG);
        verify(valueOperations).set("c", DEFAULT_EMPTY_MSG, 30L, TimeUnit.SECONDS);

        server.verify();
    }

    // ------------------------
    // 예외 분기: 429 / 403 / 4xx / 503 / 5xx / 최후 Exception
    // ------------------------

    @Test
    @DisplayName("generateAnswer: 429 TooManyRequests -> MSG_429 + FAIL_TTL(30초) 캐시")
    void generateAnswer_429_cacheFailTtl() {
        when(valueOperations.get(anyString())).thenReturn(null);

        server.expect(once(), requestTo(BASE_URL))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS));

        String result = service.generateAnswer("D");

        assertThat(result).isEqualTo(MSG_429);
        verify(valueOperations).set("d", MSG_429, 30L, TimeUnit.SECONDS);

        server.verify();
    }

    @Test
    @DisplayName("generateAnswer: 403 Forbidden -> MSG_403 + FAIL_TTL(30초) 캐시")
    void generateAnswer_403_cacheFailTtl() {
        when(valueOperations.get(anyString())).thenReturn(null);

        server.expect(once(), requestTo(BASE_URL))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withStatus(HttpStatus.FORBIDDEN));

        String result = service.generateAnswer("E");

        assertThat(result).isEqualTo(MSG_403);
        verify(valueOperations).set("e", MSG_403, 30L, TimeUnit.SECONDS);

        server.verify();
    }

    @Test
    @DisplayName("generateAnswer: 기타 4xx(예: 400) -> MSG_4XX + FAIL_TTL(30초) 캐시")
    void generateAnswer_other4xx_cacheFailTtl() {
        when(valueOperations.get(anyString())).thenReturn(null);

        server.expect(once(), requestTo(BASE_URL))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST));

        String result = service.generateAnswer("F");

        assertThat(result).isEqualTo(MSG_4XX);
        verify(valueOperations).set("f", MSG_4XX, 30L, TimeUnit.SECONDS);

        server.verify();
    }

    @Test
    @DisplayName("generateAnswer: 503 ServiceUnavailable -> MSG_503 + FAIL_TTL(30초) 캐시")
    void generateAnswer_503_cacheFailTtl() {
        when(valueOperations.get(anyString())).thenReturn(null);

        server.expect(once(), requestTo(BASE_URL))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));

        String result = service.generateAnswer("G");

        assertThat(result).isEqualTo(MSG_503);
        verify(valueOperations).set("g", MSG_503, 30L, TimeUnit.SECONDS);

        server.verify();
    }

    @Test
    @DisplayName("generateAnswer: 기타 5xx(예: 500) -> MSG_5XX + FAIL_TTL(30초) 캐시")
    void generateAnswer_other5xx_cacheFailTtl() {
        when(valueOperations.get(anyString())).thenReturn(null);

        server.expect(once(), requestTo(BASE_URL))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withServerError());

        String result = service.generateAnswer("H");

        assertThat(result).isEqualTo(MSG_5XX);
        verify(valueOperations).set("h", MSG_5XX, 30L, TimeUnit.SECONDS);

        server.verify();
    }

    // ------------------------
    // getReviewSummary()는 generateAnswer() 위임만 검증
    // ------------------------

    @Test
    @DisplayName("getReviewSummary: 리뷰 길이 필터(>5) 반영된 프롬프트로 generateAnswer 호출(간접 검증: 캐시 키로 확인)")
    void getReviewSummary_delegatesToGenerateAnswer() {
        // 캐시 miss 유도
        when(valueOperations.get(anyString())).thenReturn(null);

        // Gemini 응답 1회
        server.expect(once(), requestTo(BASE_URL))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess(successJson("SUMMARY"), MediaType.APPLICATION_JSON));

        String result = service.getReviewSummary("책제목", List.of(
                "짧",            // length 1 -> 제외
                "짧아",          // length 2 -> 제외
                "12345",         // length 5 -> 제외(>5 조건)
                "이 리뷰는 포함되어야 합니다", // 포함
                "또 하나 포함될 리뷰입니다"    // 포함
        ));

        assertThat(result).isEqualTo("SUMMARY");
        server.verify();

        // 캐시 set 호출 6시간(성공)
        verify(valueOperations).set(anyString(), anyString(), eq(21600L), eq(TimeUnit.SECONDS));
    }

    // ------------------------
    // helper
    // ------------------------

    private static String successJson(String text) {
        // GeminiResponse: candidates[0].content.parts[0].text
        // :contentReference[oaicite:5]{index=5} 구조에 맞춤
        return """
                {
                  "candidates": [
                    {
                      "content": {
                        "parts": [
                          { "text": "%s" }
                        ]
                      }
                    }
                  ]
                }
                """.formatted(escapeJson(text));
    }

    private static String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"");
    }
}
