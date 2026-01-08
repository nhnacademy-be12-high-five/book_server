package com.nhnacademy.book_server.controller;

import com.nhnacademy.book_server.dto.BookResponse;
import com.nhnacademy.book_server.dto.BookSortType;
import com.nhnacademy.book_server.dto.SearchResult;
import com.nhnacademy.book_server.service.search.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * SearchController 전체 단위 테스트 (Security 필터 제거)
 */
@WebMvcTest(SearchController.class)
@AutoConfigureMockMvc(addFilters = false)
class SearchControllerTest {

    @Autowired MockMvc mockMvc;

    // 컨트롤러 인스턴스를 가져와서 ragReindexEnabled를 테스트마다 직접 on/off 합니다.
    @Autowired SearchController searchController;

    /* ===== Controller 의존성: MockitoBean ===== */
    @MockitoBean
    BookSearchService bookSearchService;
    @MockitoBean BookReindexService bookReindexService;
    @MockitoBean RagSearchable ragSearchable;
    @MockitoBean GeminiTextClientService geminiTextClientService;
    @MockitoBean RagAnswerService ragAnswerService;

    @BeforeEach
    void setUp() {
        // 기본은 OFF로 두고, 필요한 테스트에서만 true로 켭니다.
        // (필드명이 컨트롤러와 다르면 여기 문자열만 맞춰주세요)
        ReflectionTestUtils.setField(searchController, "ragReindexEnabled", false);
    }

    /* =========================
       /api/search (GET)
       ========================= */

    @Test
    @DisplayName("GET /api/search - 정상 200(Page 반환)")
    void searchBooks_정상_200_페이지반환() throws Exception {
        Page<BookResponse> page = new PageImpl<>(List.of(), PageRequest.of(0, 20), 0);

        when(bookSearchService.searchBooks("유아", BookSortType.POPULAR, 0, 20))
                .thenReturn(page);

        mockMvc.perform(get("/api/search")
                        .param("keyword", "유아")
                        .param("sort", "POPULAR")
                        .param("page", "0")
                        .param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").exists());

        verify(bookSearchService).searchBooks("유아", BookSortType.POPULAR, 0, 20);
    }

    /* =========================
       /api/search/reindex (POST)
       ========================= */

    @Test
    @DisplayName("POST /api/search/reindex - 정상 200")
    void reindex_정상_200() throws Exception {
        when(bookReindexService.reindexAll()).thenReturn(123L);

        mockMvc.perform(post("/api/search/reindex"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("총 123권")));

        verify(bookReindexService).reindexAll();
    }

    @Test
    @DisplayName("POST /api/search/reindex - 예외 500")
    void reindex_예외_500() throws Exception {
        when(bookReindexService.reindexAll()).thenThrow(new RuntimeException("boom"));

        mockMvc.perform(post("/api/search/reindex"))
                .andExpect(status().isInternalServerError())
                .andExpect(content().string(containsString("서버 오류")));

        verify(bookReindexService).reindexAll();
    }

    /* =========================
       /api/search/reindex-rag (POST)
       ========================= */

    @Test
    @DisplayName("POST /api/search/reindex-rag - 가드 OFF면 503, reindexBooks 미호출")
    void reindexRag_가드OFF_503() throws Exception {
        // setUp()에서 false로 설정되어 있음

        mockMvc.perform(post("/api/search/reindex-rag"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(content().string(containsString("비활성화")))
                .andExpect(content().string(containsString("rag.reindex.enabled=false")));

        verifyNoInteractions(ragSearchable);
    }

    @Test
    @DisplayName("POST /api/search/reindex-rag - ON이면 200, reindexBooks 호출")
    void reindexRag_ON_정상_200() throws Exception {
        ReflectionTestUtils.setField(searchController, "ragReindexEnabled", true);

        mockMvc.perform(post("/api/search/reindex-rag"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("실행했습니다")));

        verify(ragSearchable, times(1)).reindexBooks();
    }

    @Test
    @DisplayName("POST /api/search/reindex-rag - ON인데 예외면 500, 메시지에 예외 포함")
    void reindexRag_ON_예외_500() throws Exception {
        ReflectionTestUtils.setField(searchController, "ragReindexEnabled", true);

        doThrow(new RuntimeException("boom"))
                .when(ragSearchable).reindexBooks();

        mockMvc.perform(post("/api/search/reindex-rag"))
                .andExpect(status().isInternalServerError())
                .andExpect(content().string(containsString("RAG reindex 중 서버 오류")))
                .andExpect(content().string(containsString("boom")));

        verify(ragSearchable, times(1)).reindexBooks();
    }

    /* =========================
       /api/search/rag-search (GET)
       ========================= */

    @Test
    @DisplayName("GET /api/search/rag-search - 정상 200(List 반환)")
    void ragSearch_정상_200_리스트반환() throws Exception {
        SearchResult<BookResponse> sr = new SearchResult<>(List.of(), 0L);

        when(ragSearchable.searchByRag("유아", 0, 10))
                .thenReturn(sr);

        mockMvc.perform(get("/api/search/rag-search")
                        .param("keyword", "유아"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());

        verify(ragSearchable).searchByRag("유아", 0, 10);
    }

    /* =========================
       /api/search/rag-answer (GET)
       ========================= */

    @Test
    @DisplayName("GET /api/search/rag-answer - 정상 200(문자열 반환)")
    void ragAnswer_정상_200_문자열반환() throws Exception {
        when(ragAnswerService.answer("유아")).thenReturn("추천 문장");

        mockMvc.perform(get("/api/search/rag-answer")
                        .param("keyword", "유아"))
                .andExpect(status().isOk())
                .andExpect(content().string("추천 문장"));

        verify(ragAnswerService).answer("유아");
    }
}
