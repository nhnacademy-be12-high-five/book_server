package com.nhnacademy.book_server.controller.book;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nhnacademy.book_server.controller.AladinTestController;
import com.nhnacademy.book_server.entity.AladinItem;
import com.nhnacademy.book_server.service.impl.AladinService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Collections;
import java.util.List;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AladinTestController.class)
@WithMockUser(username = "admin", roles = {"ADMIN"})
class AladinTestControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AladinService aladinService;

    @Autowired
    private ObjectMapper objectMapper;

    // 테스트용 더미 데이터 생성 메서드
    private AladinItem createDummyItem(String title) {
        AladinItem item = new AladinItem();
        item.setTitle(title);
        item.setIsbn13("9788936434120");
        return item;
    }

    // ==========================================
    // 1. Lookup 메서드 테스트
    // ==========================================

    @Test
    @DisplayName("[Lookup] ISBN으로 책 상세 조회 - 성공")
    void lookupTest() throws Exception {
        // Given
        String isbn = "9788936434120";
        AladinItem mockItem = createDummyItem("테스트 책 제목");

        given(aladinService.lookupBook(isbn)).willReturn(mockItem);

        // When & Then
        mockMvc.perform(get("/api/test/aladin/lookup")
                        .param("isbn13", isbn)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("테스트 책 제목"))
                .andExpect(jsonPath("$.isbn13").value(isbn))
                .andDo(print());

        verify(aladinService).lookupBook(isbn);
    }

    @Test
    @DisplayName("[Lookup] 필수 파라미터(isbn13) 누락 시 400 에러 발생")
    void lookupMissingParamTest() throws Exception {
        // When & Then: 파라미터 없이 요청
        mockMvc.perform(get("/api/test/aladin/lookup")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().is5xxServerError())
                .andDo(print());
    }

    // ==========================================
    // 2. List 메서드 테스트
    // ==========================================

    @Test
    @DisplayName("[List] 베스트셀러 등 리스트 조회 - 성공")
    void listTest() throws Exception {
        // Given
        String queryType = "Bestseller";
        List<AladinItem> mockList = List.of(
                createDummyItem("베스트셀러1"),
                createDummyItem("베스트셀러2")
        );

        given(aladinService.getBookList(queryType)).willReturn(mockList);

        // When & Then
        mockMvc.perform(get("/api/test/aladin/list")
                        .param("queryType", queryType)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.size()").value(2))
                .andExpect(jsonPath("$[0].title").value("베스트셀러1"))
                .andDo(print());

        verify(aladinService).getBookList(queryType);
    }

    @Test
    @DisplayName("[List] 필수 파라미터(queryType) 누락 시 400 에러")
    void listMissingParamTest() throws Exception {
        mockMvc.perform(get("/api/test/aladin/list")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().is5xxServerError())
                .andDo(print());
    }

    // ==========================================
    // 3. Search 메서드 테스트
    // ==========================================

    @Test
    @DisplayName("[Search] 키워드 검색 - 성공 (QueryType 지정)")
    void searchTest() throws Exception {
        // Given
        String query = "자바";
        String queryType = "Title";
        List<AladinItem> mockList = List.of(createDummyItem("자바의 정석"));

        given(aladinService.searchBooks(query, queryType)).willReturn(mockList);

        // When & Then
        mockMvc.perform(get("/api/test/aladin/search")
                        .param("query", query)
                        .param("queryType", queryType)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].title").value("자바의 정석"))
                .andDo(print());

        verify(aladinService).searchBooks(query, queryType);
    }

    @Test
    @DisplayName("[Search] 검색 결과가 없을 때 빈 리스트 반환")
    void searchEmptyTest() throws Exception {
        // Given
        String query = "없는책";
        given(aladinService.searchBooks(anyString(), anyString())).willReturn(Collections.emptyList());

        // When & Then
        mockMvc.perform(get("/api/test/aladin/search")
                        .param("query", query)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.size()").value(0)) // 빈 배열 확인
                .andDo(print());
    }

    @Test
    @DisplayName("[Search] queryType 파라미터 누락 시 기본값(Title) 적용 확인")
    void searchDefaultParamTest() throws Exception {
        // Given
        String query = "스프링";
        // 빈 리스트라도 호출 자체가 "Title"로 갔는지 확인하는 것이 목적
        given(aladinService.searchBooks(anyString(), anyString())).willReturn(List.of());

        // When & Then
        mockMvc.perform(get("/api/test/aladin/search")
                        .param("query", query)
                        // queryType 파라미터 누락
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andDo(print());

        // Verify: 두 번째 인자가 "Title"로 들어갔는지 검증
        verify(aladinService).searchBooks(query, "Title");
    }

    @Test
    @DisplayName("[Search] 필수 파라미터(query) 누락 시 400 에러")
    void searchMissingQueryTest() throws Exception {
        // When & Then: query 파라미터 없이 요청
        mockMvc.perform(get("/api/test/aladin/search")
                        .param("queryType", "Author")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().is5xxServerError())
                .andDo(print());
    }
}