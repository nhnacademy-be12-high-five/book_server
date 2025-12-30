package com.nhnacademy.book_server.controller.book;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nhnacademy.book_server.controller.AladinTestController;
import com.nhnacademy.book_server.entity.AladinItem;
import com.nhnacademy.book_server.service.impl.AladinService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
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
    private MockMvc mockMvc; // HTTP 요청 시뮬레이션

    @MockBean
    private AladinService aladinService; // Service는 Mocking

    @Autowired
    private ObjectMapper objectMapper;

    // 테스트용 더미 데이터 생성 메서드
    private AladinItem createDummyItem(String title) {
        // AladinItem의 실제 필드 구성에 맞춰서 생성 (Setter 혹은 Builder 사용)
        // 여기서는 예시로 객체를 생성합니다. 실제 Entity 구조에 맞게 수정해주세요.
        AladinItem item = new AladinItem();
        item.setTitle(title);
        item.setIsbn13("9788936434120");
        return item;
    }

    @Test
    @DisplayName("ISBN으로 책 상세 조회 (lookup)")
    void lookupTest() throws Exception {
        // Given
        String isbn = "9788936434120";
        AladinItem mockItem = createDummyItem("테스트 책 제목");

        // Service가 호출되었을 때 반환할 값 정의
        given(aladinService.lookupBook(isbn)).willReturn(mockItem);

        // When & Then
        mockMvc.perform(get("/api/test/aladin/lookup")
                        .param("isbn13", isbn) // 요청 파라미터
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk()) // 200 OK 확인
                .andExpect(jsonPath("$.title").value("테스트 책 제목")) // JSON 응답 검증
                .andExpect(jsonPath("$.isbn13").value(isbn))
                .andDo(print()); // 로그 출력

        // Verify: 서비스 메서드가 실제로 호출되었는지 검증
        verify(aladinService).lookupBook(isbn);
    }

    @Test
    @DisplayName("베스트셀러 등 리스트 조회 (list)")
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
                .andExpect(jsonPath("$.size()").value(2)) // 리스트 크기 확인
                .andExpect(jsonPath("$[0].title").value("베스트셀러1"))
                .andDo(print());

        verify(aladinService).getBookList(queryType);
    }

    @Test
    @DisplayName("키워드 검색 (search)")
    void searchTest() throws Exception {
        // Given
        String query = "자바";
        String queryType = "Title";
        List<AladinItem> mockList = List.of(createDummyItem("자바의 정석"));

        given(aladinService.searchBooks(eq(query), eq(queryType))).willReturn(mockList);

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
    @DisplayName("검색 시 queryType 파라미터 누락 시 기본값(Title) 적용 확인")
    void searchDefaultParamTest() throws Exception {
        // Given
        String query = "스프링";
        // Controller에서 @RequestParam(defaultValue = "Title")이 동작하는지 확인
        given(aladinService.searchBooks(anyString(), anyString())).willReturn(List.of());

        // When & Then
        mockMvc.perform(get("/api/test/aladin/search")
                        .param("query", query)
                        // queryType 파라미터를 보내지 않음
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andDo(print());

        // Verify: 두 번째 인자가 "Title"로 들어갔는지 확인
        verify(aladinService).searchBooks(query, "Title");
    }
}
