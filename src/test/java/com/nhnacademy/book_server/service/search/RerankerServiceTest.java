package com.nhnacademy.book_server.service.search;

import com.nhnacademy.book_server.dto.BookResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RerankerServiceTest {

    @Mock
    RestTemplate restTemplate;

    RerankerService rerankerService;

    @BeforeEach
    void setUp() {
        rerankerService = new RerankerService(restTemplate);
    }

    @Test
    @DisplayName("books가 null이면 빈 리스트 반환 + RestTemplate 호출 없음")
    void rerank_nullBooks_returnsEmpty_andNoHttpCall() {
        List<BookResponse> result = rerankerService.rerank(null, "q");

        assertThat(result).isEmpty();
        verifyNoInteractions(restTemplate);
    }

    @Test
    @DisplayName("books가 empty이면 빈 리스트 반환 + RestTemplate 호출 없음")
    void rerank_emptyBooks_returnsEmpty_andNoHttpCall() {
        List<BookResponse> result = rerankerService.rerank(List.of(), "q");

        assertThat(result).isEmpty();
        verifyNoInteractions(restTemplate);
    }

    @Test
    @DisplayName("Reranker 응답이 null이면 원본 순서 유지")
    void rerank_resultsNull_returnsOriginalBooks() {
        List<BookResponse> books = List.of(
                mockBook("A", "a", "pa", "c1"),
                mockBook("B", "b", "pb", "c2")
        );

        when(restTemplate.postForObject(anyString(), any(HttpEntity.class), eq(List.class)))
                .thenReturn(null);

        List<BookResponse> result = rerankerService.rerank(books, "query");

        assertThat(result).isSameAs(books);
        verify(restTemplate, times(1))
                .postForObject(eq("http://reranker.java21.net/rerank"), any(HttpEntity.class), eq(List.class));
    }

    @Test
    @DisplayName("Reranker 응답이 empty이면 원본 순서 유지")
    void rerank_resultsEmpty_returnsOriginalBooks() {
        List<BookResponse> books = List.of(
                mockBook("A", "a", "pa", "c1"),
                mockBook("B", "b", "pb", "c2")
        );

        when(restTemplate.postForObject(anyString(), any(HttpEntity.class), eq(List.class)))
                .thenReturn(List.of());

        List<BookResponse> result = rerankerService.rerank(books, "query");

        assertThat(result).isSameAs(books);
        verify(restTemplate, times(1))
                .postForObject(eq("http://reranker.java21.net/rerank"), any(HttpEntity.class), eq(List.class));
    }

    @Test
    @DisplayName("정상 응답: score 내림차순으로 재정렬하여 반환")
    void rerank_validResults_sortsByScoreDesc() {
        BookResponse b0 = mockBook("T0", "A0", "P0", "C0");
        BookResponse b1 = mockBook("T1", "A1", "P1", "C1");
        BookResponse b2 = mockBook("T2", "A2", "P2", "C2");
        List<BookResponse> books = List.of(b0, b1, b2);

        // index=1 score=0.9, index=2 score=0.8, index=0 score=0.1
        List<Map<String, Object>> apiResults = List.of(
                Map.of("index", 1, "score", 0.9),
                Map.of("index", 2, "score", 0.8),
                Map.of("index", 0, "score", 0.1)
        );

        when(restTemplate.postForObject(anyString(), any(HttpEntity.class), eq(List.class)))
                .thenReturn(apiResults);

        List<BookResponse> result = rerankerService.rerank(books, "q");

        assertThat(result).containsExactly(b1, b2, b0);
    }

    @Test
    @DisplayName("응답에 유효하지 않은 index(-1/범위초과)가 섞이면 해당 항목은 스킵")
    void rerank_invalidIndex_isSkipped() {
        BookResponse b0 = mockBook("T0", "A0", "P0", "C0");
        BookResponse b1 = mockBook("T1", "A1", "P1", "C1");
        List<BookResponse> books = List.of(b0, b1);

        // 유효: index=1 score=0.5, 무효: index=-1, index=999
        List<Map<String, Object>> apiResults = List.of(
                Map.of("index", -1, "score", 10.0),
                Map.of("index", 999, "score", 9.0),
                Map.of("index", 1, "score", 0.5)
        );

        when(restTemplate.postForObject(anyString(), any(HttpEntity.class), eq(List.class)))
                .thenReturn(apiResults);

        List<BookResponse> result = rerankerService.rerank(books, "q");

        // 유효한 것만 들어가므로 b1만 반환
        assertThat(result).containsExactly(b1);
    }

    @Test
    @DisplayName("index/score가 숫자가 아니면 index=-1로 스킵되거나 score=0으로 처리")
    void rerank_nonNumericIndexOrScore_handledSafely() {
        BookResponse b0 = mockBook("T0", "A0", "P0", "C0");
        BookResponse b1 = mockBook("T1", "A1", "P1", "C1");
        List<BookResponse> books = List.of(b0, b1);

        // 1) index가 문자열 -> -1 처리되어 스킵
        // 2) score가 문자열 -> 0.0 처리 (index는 유효)
        List<Map<String, Object>> apiResults = List.of(
                new HashMap<>(Map.of("index", "1", "score", 100.0)),
                new HashMap<>(Map.of("index", 0, "score", "0.9")),
                new HashMap<>(Map.of("index", 1, "score", 0.8))
        );

        when(restTemplate.postForObject(anyString(), any(HttpEntity.class), eq(List.class)))
                .thenReturn(apiResults);

        List<BookResponse> result = rerankerService.rerank(books, "q");

        // index=1 score=0.8 가 가장 높고, index=0 score는 0.0 (문자열이라 0 처리)
        assertThat(result).containsExactly(b1, b0);
    }

    @Test
    @DisplayName("RestTemplate 호출 중 예외 발생 시 원본 순서 그대로 반환")
    void rerank_exception_returnsOriginalBooks() {
        List<BookResponse> books = List.of(
                mockBook("A", "a", "pa", "c1"),
                mockBook("B", "b", "pb", "c2")
        );

        when(restTemplate.postForObject(anyString(), any(HttpEntity.class), eq(List.class)))
                .thenThrow(new RuntimeException("boom"));

        List<BookResponse> result = rerankerService.rerank(books, "query");

        assertThat(result).isSameAs(books);
    }

    @Test
    @DisplayName("요청 바디(query/texts) 및 Content-Type=JSON 설정 + 텍스트 300자 트렁케이션 검증")
    void rerank_buildsRequestBody_andTruncatesContentTo300() {
        // content 350자
        String longContent = "x".repeat(350);
        BookResponse b0 = mockBook("TITLE0", "AUTHOR0", "PUBLISHER0", longContent);
        BookResponse b1 = mockBook("TITLE1", "AUTHOR1", "PUBLISHER1", "short");
        List<BookResponse> books = List.of(b0, b1);

        // 응답은 null로 두어 "원본 반환" 분기까지도 같이 커버
        when(restTemplate.postForObject(anyString(), any(HttpEntity.class), eq(List.class)))
                .thenReturn(null);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<HttpEntity<Map<String, Object>>> entityCaptor =
                (ArgumentCaptor) ArgumentCaptor.forClass(HttpEntity.class);

        List<BookResponse> result = rerankerService.rerank(books, "myQuery");
        assertThat(result).isSameAs(books);

        verify(restTemplate).postForObject(eq("http://reranker.java21.net/rerank"), entityCaptor.capture(), eq(List.class));

        HttpEntity<Map<String, Object>> entity = entityCaptor.getValue();
        Map<String, Object> body = entity.getBody();
        HttpHeaders headers = entity.getHeaders();

        assertThat(headers.getContentType()).isEqualTo(MediaType.APPLICATION_JSON);
        assertThat(body)
                .containsKeys("query", "texts")
                .containsEntry("query", "myQuery");

        @SuppressWarnings("unchecked")
        List<String> texts = (List<String>) body.get("texts");
        assertThat(texts).hasSize(2);

        // toRerankText() 포맷: "%s %s %s %s" (title author publisher shortContent)
        String text0 = texts.get(0);
        assertThat(text0).contains("TITLE0", "AUTHOR0", "PUBLISHER0");

        // 300자로 잘렸는지 확인: 300자만 포함되고 301번째는 없어야 함
        String expected300 = "x".repeat(300);
        assertThat(text0)
                .contains("TITLE0", "AUTHOR0", "PUBLISHER0")
                .contains(expected300)
                .doesNotContain("x".repeat(301));

        String text1 = texts.get(1);
        assertThat(text1).contains("TITLE1", "AUTHOR1", "PUBLISHER1", "short");
    }

    // --------------------------
    // Test helper
    // --------------------------
    private BookResponse mockBook(String title, String author, String publisher, String content) {
        BookResponse book = mock(BookResponse.class);
        when(book.title()).thenReturn(title);
        when(book.author()).thenReturn(author);
        when(book.publisher()).thenReturn(publisher);
        when(book.content()).thenReturn(content);
        return book;
    }
}
