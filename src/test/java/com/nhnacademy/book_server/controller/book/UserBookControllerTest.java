package com.nhnacademy.book_server.controller.book;


import com.fasterxml.jackson.databind.ObjectMapper;
import com.nhnacademy.book_server.controller.UserBookController;
import com.nhnacademy.book_server.dto.BookResponse;
import com.nhnacademy.book_server.dto.response.GetBookResponse;
import com.nhnacademy.book_server.service.BookService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Collections;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(UserBookController.class)
@WithMockUser
class UserBookControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private BookService bookService;

    @MockitoBean
    private StringRedisTemplate redisTemplate;

    @MockitoBean
    private ValueOperations<String, String> valueOperations;

    @Autowired
    private ObjectMapper objectMapper;

    // 공통적으로 사용할 테스트용 BookResponse 생성 (Helper)
    private BookResponse createTestBookResponse(Long id) {
        return new BookResponse(
                id,
                "테스트 도서 " + id,
                "작가",
                "1234567890123",
                10000,
                "http://image.url",
                Collections.emptyList(), // categories
                Collections.emptyList(), // tags
                "내용",
                "출판사",
                "2024-01-01",
                4.5,
                100L,
                null,
                null,
                null,
                null
        );
    }

    @Test
    @DisplayName("도서 전체 조회 (GET /api/books)")
    void getAllBooks() throws Exception {
        // given
        BookResponse book = createTestBookResponse(1L);
        Page<BookResponse> page = new PageImpl<>(List.of(book));

        given(bookService.findAllBooks(any(Pageable.class))).willReturn(page);

        // when & then
        mockMvc.perform(get("/api/books")
                        .param("page", "0")
                        .param("size", "10")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].title").value("테스트 도서 1"))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    @DisplayName("도서 상세 조회 (GET /api/books/{id})")
    void getBookById() throws Exception {
        // given
        Long bookId = 1L;
        BookResponse book = createTestBookResponse(bookId);

        given(bookService.findBookById(bookId)).willReturn(book);

        // when & then
        mockMvc.perform(get("/api/books/{id}", bookId)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("테스트 도서 1"));
    }

    @Test
    @DisplayName("신간 도서 조회 (GET /api/books/new)")
    void getNewBooks() throws Exception {
        // given
        List<BookResponse> books = List.of(createTestBookResponse(10L));
        given(bookService.getNewBooks()).willReturn(books);

        // when & then
        mockMvc.perform(get("/api/books/new")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.size()").value(1))
                .andExpect(jsonPath("$[0].title").value("테스트 도서 10"));
    }

    @Test
    @DisplayName("주간 인기 도서 조회 (GET /api/books/popular)")
    void getWeeklyPopular() throws Exception {
        // given
        List<BookResponse> books = List.of(createTestBookResponse(20L));
        given(bookService.getWeeklyPopularBooks(anyInt())).willReturn(books);

        // when & then
        mockMvc.perform(get("/api/books/popular")
                        .param("size", "5")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.size()").value(1));
    }

    @Test
    @DisplayName("판매량 반영 (POST /api/books/{bookId}/best-seller)")
    void updateBestSellerScore() throws Exception {
        // given
        Long bookId = 1L;
        Integer quantity = 3;

        // when & then
        mockMvc.perform(post("/api/books/{bookId}/best-seller", bookId)
                        .with(csrf()) // POST 요청 필수
                        .content(objectMapper.writeValueAsString(quantity))
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());

        verify(bookService).incrementBestSellerScore(bookId, quantity);
    }

    @Test
    @DisplayName("베스트셀러 조회 (GET /api/books/best-seller)")
    void getBestSeller() throws Exception {
        // given
        List<BookResponse> books = List.of(createTestBookResponse(30L));
        given(bookService.getBestSeller(anyInt())).willReturn(books);

        // when & then
        mockMvc.perform(get("/api/books/best-seller")
                        .param("size", "5")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.size()").value(1));
    }

    @Test
    @DisplayName("벌크 조회  /api/books/bulk)")
    void getBooksBulk() throws Exception {
        // given
        List<Long> bookIds = List.of(1L, 2L);
        List<GetBookResponse> responses = List.of(
                new GetBookResponse(1L, "책1", 10000, "url1"),
                new GetBookResponse(2L, "책2", 20000, "url2")
        );

        given(bookService.getBooksBulk(bookIds)).willReturn(responses);

        // when & then
        mockMvc.perform(post("/api/books/bulk")
                        .with(csrf())
                        .content(objectMapper.writeValueAsString(bookIds))
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.size()").value(2))
                .andExpect(jsonPath("$[0].title").value("책1"));
    }
}