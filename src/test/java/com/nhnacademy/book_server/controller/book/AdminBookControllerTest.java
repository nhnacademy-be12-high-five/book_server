package com.nhnacademy.book_server.controller.book;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nhnacademy.book_server.controller.AdminBookController;
import com.nhnacademy.book_server.dto.BookResponse;
import com.nhnacademy.book_server.dto.request.BookUpdateRequest;
import com.nhnacademy.book_server.entity.Book;
import com.nhnacademy.book_server.parser.ParsingDto;
import com.nhnacademy.book_server.service.BookRegistrationService;
import com.nhnacademy.book_server.service.BookService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AdminBookController.class) // 컨트롤러 계층만 로드
class AdminBookControllerTest {

    @Autowired
    private MockMvc mockMvc; // HTTP 요청 시뮬레이션

    @Autowired
    private ObjectMapper objectMapper; // Java 객체 <-> JSON 변환

    @MockitoBean
    private BookService bookService; // 가짜 서비스 객체

    @MockitoBean
    private BookRegistrationService bookRegistrationService; // 가짜 서비스 객체

    @Test
    @DisplayName("관리자 도서 등록 성공")
    @WithMockUser(username = "admin", roles = "ADMIN") // 관리자 권한 부여
    void createBook() throws Exception {
        // given

        ParsingDto requestDto = new ParsingDto();
        requestDto.setIsbn("1234567890123");
        requestDto.setTitle("테스트 도서");
        requestDto.setAuthor("테스트 작가");
        requestDto.setPublisher("NHN출판");
        requestDto.setPubDate("2024-12-24");
        requestDto.setPrice("15000");

        // 서비스가 호출되면 임의의 Book 엔티티 반환 (컨트롤러 로직상 반환값은 중요하지 않음)
        given(bookService.createBook(any(ParsingDto.class)))
                .willReturn(Book.builder().title("테스트 도서").build());

        // when & then
        mockMvc.perform(post("/api/admin/books")
                        .with(csrf()) // POST 요청은 CSRF 토큰 필요
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(requestDto)))
                .andDo(print())
                .andExpect(status().isCreated()) // 201 Created 검증
                .andExpect(jsonPath("$.isbn").value("1234567890123"))
                .andExpect(jsonPath("$.title").value("테스트 도서"));
    }

    @Test
    @DisplayName("도서 전체 조회 성공")
    @WithMockUser(username = "admin", roles = "ADMIN")
    void getAllBooks() throws Exception {
        // given
        BookResponse response = BookResponse.builder()
                .bookId(1L)
                .title("리스트 테스트용 책")
                .build();


        // Page<BookResponse> 생성
        Page<BookResponse> pageResult = new PageImpl<>(List.of(response));

        given(bookService.findAllBooks(any(Pageable.class)))
                .willReturn(pageResult);

        // when & then
        mockMvc.perform(get("/api/admin/books")
                        .param("page", "0")
                        .param("size", "10")
                        .contentType(MediaType.APPLICATION_JSON))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].title").value("리스트 테스트용 책"));
    }
//
    @Test
    @DisplayName("책 한 권 조회 성공")
    @WithMockUser(username = "admin", roles = "ADMIN")
    void getBookById_Success() throws Exception {
        // given
        Long bookId = 1L;
        BookResponse response = BookResponse.builder()
                .bookId(bookId)
                .title("단건 조회 책")
                .build();

        given(bookService.findBookById(bookId)).willReturn(response);

        // when & then
        mockMvc.perform(get("/api/admin/books/{id}", bookId))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("단건 조회 책"));
    }
//
    @Test
    @DisplayName("책 한 권 조회 실패 - 존재하지 않는 ID (404)")
    @WithMockUser(username = "admin", roles = "ADMIN")
    void getBookById_NotFound() throws Exception {
        // given
        Long bookId = 999L;
        // 서비스에서 예외 발생 시뮬레이션
        given(bookService.findBookById(bookId))
                .willThrow(new RuntimeException("Not Found"));

        // when & then
        mockMvc.perform(get("/api/admin/books/{id}", bookId))
                .andDo(print())
                .andExpect(status().isNotFound()); // 404 Not Found 검증
    }

    @Test
    @DisplayName("도서 수정 성공")
    @WithMockUser(username = "admin", roles = "ADMIN")
    void updateBook() throws Exception {
        // given
        Long bookId = 1L;
        BookUpdateRequest updateRequest = new BookUpdateRequest();
        // 필요한 경우 Setter로 값 주입 (예: updateRequest.setPrice(20000);)

        BookResponse updatedResponse = BookResponse.builder()
                .bookId(bookId)
                .title("수정된 책")
                .build();


        given(bookService.updateBook(eq(bookId), any(BookUpdateRequest.class)))
                .willReturn(updatedResponse);

        // when & then
        mockMvc.perform(put("/api/admin/books/{id}", bookId)
                        .with(csrf()) // PUT 요청도 CSRF 토큰 필요
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateRequest)))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("수정된 책"));
    }

    @Test
    @DisplayName("AI/API 도서 정보 검색 (BookRegistrationService 호출)")
    @WithMockUser(username = "admin", roles = "ADMIN")
    void searchBookWithAi() throws Exception {
        // given
        String isbn = "9791112345678";

        ParsingDto searchResult = new ParsingDto();
        searchResult.setIsbn(isbn);
        searchResult.setTitle("Gemini가 찾아준 책");
        searchResult.setDescription("AI가 작성한 서평입니다.");

        given(bookRegistrationService.getBookInfoWithAi(isbn))
                .willReturn(searchResult);

        // when & then
        mockMvc.perform(get("/api/admin/books/search-api")
                        .param("isbn", isbn))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isbn").value(isbn))
                .andExpect(jsonPath("$.title").value("Gemini가 찾아준 책"))
                .andExpect(jsonPath("$.description").value("AI가 작성한 서평입니다."));
    }
}