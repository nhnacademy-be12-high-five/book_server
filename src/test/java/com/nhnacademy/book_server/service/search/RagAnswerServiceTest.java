package com.nhnacademy.book_server.service.search;

import com.nhnacademy.book_server.dto.BookResponse;
import com.nhnacademy.book_server.dto.SearchResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RagAnswerServiceTest {

    @Mock RagSearchService ragSearchService;
    @Mock RerankerService rerankerService;
    @Mock GeminiTextClientService geminiTextClientService;

    @InjectMocks RagAnswerService ragAnswerService;

    @Captor ArgumentCaptor<String> promptCaptor;

    // -----------------------
    // Helper: BookResponse 생성
    // -----------------------
    private BookResponse book(long id, String title, String author, int price, String content) {
        // 프로젝트 BookResponse record/생성자 시그니처에 맞춰야 합니다.
        // 사용자가 다른 테스트에서 사용한 생성자 형태를 최대한 유지했습니다.
        return new BookResponse(
                id,
                title,
                author,
                "isbn",
                price,
                "img",
                List.of(),      // categories
                List.of(),      // tags
                content,        // content
                "publisher",
                "2024-01-01",
                4.0,
                10L,
                null,
                null,
                null,
                null
        );
    }

    private SearchResult<BookResponse> sr(long total, List<BookResponse> content) {
        // SearchResult가 record 라면 (totalHits, content) 순서에 맞춰 수정 필요
        return new SearchResult<>(content, total);
    }

    @Test
    @DisplayName("answer: 후보군이 비어있으면 고정 메시지 반환 + rerank/gemini 호출 없음")
    void answer_emptyCandidates_returnsFallback_andNoFurtherCalls() {
        when(ragSearchService.searchByRag("지리산", 0, 50))
                .thenReturn(sr(0, List.of()));

        String out = ragAnswerService.answer("지리산");

        assertThat(out).isEqualTo("해당 키워드와 관련된 도서를 찾을 수 없어 답변을 생성하기 어렵습니다.");

        verify(ragSearchService).searchByRag("지리산", 0, 50);
        verifyNoInteractions(rerankerService);
        verifyNoInteractions(geminiTextClientService);
    }

    @Test
    @DisplayName("answer: 정상 흐름 -> rerank 호출 + 상위 5개로 prompt 구성 + gemini 호출 및 응답 반환")
    void answer_success_rerankTop5_promptAndGeminiCalled() {
        String keyword = "자바";

        List<BookResponse> candidates = List.of(
                book(1, "A", "a1", 1000, "c1"),
                book(2, "B", "a2", 2000, "c2"),
                book(3, "C", "a3", 3000, "c3"),
                book(4, "D", "a4", 4000, "c4"),
                book(5, "E", "a5", 5000, "c5"),
                book(6, "F", "a6", 6000, "c6") // 6개 -> top5 제한 검증
        );

        when(ragSearchService.searchByRag(keyword, 0, 50))
                .thenReturn(sr(6, candidates));

        // reranker가 “정렬된 결과”를 돌려준다고 가정 (여기서는 그대로 반환)
        when(rerankerService.rerank(candidates, keyword))
                .thenReturn(candidates);

        when(geminiTextClientService.generateAnswer(anyString()))
                .thenReturn("GEMINI_ANSWER");

        String out = ragAnswerService.answer(keyword);

        assertThat(out).isEqualTo("GEMINI_ANSWER");

        verify(ragSearchService).searchByRag(keyword, 0, 50);
        verify(rerankerService).rerank(candidates, keyword);

        verify(geminiTextClientService).generateAnswer(promptCaptor.capture());
        String prompt = promptCaptor.getValue();

        // ---- prompt 핵심 포함 여부 ----
        assertThat(prompt)
                .contains("사용자 질문: \"")
                .contains(keyword)

                // 1~5번 도서만 포함
                .contains("1. 제목: A")
                .contains("2. 제목: B")
                .contains("3. 제목: C")
                .contains("4. 제목: D")
                .contains("5. 제목: E")

                // 6번 도서는 포함되면 안 됨
                .doesNotContain("6. 제목: F")

                // 저자/가격 형식 포함
                .contains("저자: a1")
                .contains("가격: 1000원");
    }

    @Test
    @DisplayName("answer: content가 null인 도서가 있어도 NPE 없이 prompt에 빈 설명으로 들어간다")
    void answer_nullContent_safeTruncate() {
        String keyword = "테스트";

        List<BookResponse> candidates = List.of(
                book(1, "NullContentBook", "author", 1000, null)
        );

        when(ragSearchService.searchByRag(keyword, 0, 50))
                .thenReturn(sr(1, candidates));
        when(rerankerService.rerank(candidates, keyword))
                .thenReturn(candidates);
        when(geminiTextClientService.generateAnswer(anyString()))
                .thenReturn("OK");

        String out = ragAnswerService.answer(keyword);
        assertThat(out).isEqualTo("OK");

        verify(geminiTextClientService).generateAnswer(promptCaptor.capture());
        String prompt = promptCaptor.getValue();

        // 설명 라인이 빈 문자열로 들어가야 함 (truncate가 null -> "" 처리)
        assertThat(prompt).contains("설명: ").doesNotContain("null");
    }

    @Test
    @DisplayName("answer: content 200자 초과 시 ...으로 잘려서 prompt에 들어간다")
    void answer_longContent_truncatedTo200WithEllipsis() {
        String keyword = "긴글";
        String longText = "x".repeat(250); // 250자

        List<BookResponse> candidates = List.of(
                book(1, "LongContentBook", "author", 1000, longText)
        );

        when(ragSearchService.searchByRag(keyword, 0, 50))
                .thenReturn(sr(1, candidates));
        when(rerankerService.rerank(candidates, keyword))
                .thenReturn(candidates);
        when(geminiTextClientService.generateAnswer(anyString()))
                .thenReturn("OK");

        ragAnswerService.answer(keyword);

        verify(geminiTextClientService).generateAnswer(promptCaptor.capture());
        String prompt = promptCaptor.getValue();

        // truncate 결과: 200자 + "..."
        // "설명: " 이후에 200자 + ... 이 들어가야 함
        int idx = prompt.indexOf("설명: ");
        assertThat(idx).isNotNegative();

        String after = prompt.substring(idx);
        // "설명: " + 200자 + "..." 포함 여부를 느슨하게 검증
        assertThat(after).contains("설명: " + "x".repeat(200) + "...");
    }
}
