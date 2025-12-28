
package com.nhnacademy.book_server.controller.book;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nhnacademy.book_server.controller.AdminBookController;
import com.nhnacademy.book_server.dto.BookResponse;
import com.nhnacademy.book_server.dto.request.BookCreateRequest;
import com.nhnacademy.book_server.dto.request.BookUpdateRequest;
import com.nhnacademy.book_server.entity.Book;
import com.nhnacademy.book_server.parser.ParsingDto;
import com.nhnacademy.book_server.service.BookRegistrationService;
import com.nhnacademy.book_server.service.BookService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Collections;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AdminBookController.class)
@WithMockUser(username = "admin", roles = {"ADMIN"}) // [핵심 1] 모든 테스트에 관리자 권한 부여
class AdminBookControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private BookService bookService;

    @MockBean
    private BookRegistrationService bookRegistrationService;

    // BookResponse 생성을 돕는 헬퍼 메서드 (Record에 빌더가 없으므로 사용)
    private BookResponse createMockBookResponse(Long id, String title) {
        return new BookResponse(
                id,
                title,
                "Test Author",
                "9781234567890",
                15000,
                "http://image.url",
                Collections.emptyList(), // categories
                Collections.emptyList(), // tags
                "Test Content",
                "Test Publisher",
                "2023-01-01",
                4.5,
                10L,
                "AI Summary",
                "AI Review Summary"
        );
    }

    @Test
    @DisplayName("도서 생성 성공")
    void createBook() throws Exception {
        // given
        BookCreateRequest request = new BookCreateRequest();
        // 리플렉션 등으로 필드 주입 필요하다면 여기에 작성 (생략)

        Book mockBook = Book.builder()
                .id(1L)
                .title("테스트 책")
                .isbn13("9791112345678")
                .price(10000)
                .reviewCount(1)
                .build();

        given(bookService.createBook(any(BookCreateRequest.class))).willReturn(mockBook);

        // when & then
        mockMvc.perform(post("/api/admin/books")
                        .contentType(MediaType.APPLICATION_JSON)
                        .with(csrf())
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.title").value("테스트 책"))
                .andExpect(jsonPath("$.isbn").value("9791112345678")) // Entity 필드명 확인 필요 (isbn -> isbn13)
                .andDo(print());
    }

    @Test
    @DisplayName("도서 전체 조회 (페이징)")
    void getAllBooks() throws Exception {
        // given
        BookResponse response1 = createMockBookResponse(1L, "책1");
        BookResponse response2 = createMockBookResponse(2L, "책2");

        Page<BookResponse> mockPage = new PageImpl<>(List.of(response1, response2));

        given(bookService.findAllBooks(any(Pageable.class))).willReturn(mockPage);

        // when & then
        mockMvc.perform(get("/api/admin/books")
                        .param("page", "0")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].title").value("책1"))
                .andExpect(jsonPath("$[1].title").value("책2"))
                .andDo(print());
    }

    @Test
    @DisplayName("도서 단건 조회 - 성공")
    void getBookById_Success() throws Exception {
        // given
        Long bookId = 1L;
        BookResponse response = createMockBookResponse(bookId, "상세 조회 책");

        given(bookService.findBookById(bookId)).willReturn(response);

        // when & then
        mockMvc.perform(get("/api/admin/books/{id}", bookId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(bookId))
                .andExpect(jsonPath("$.title").value("상세 조회 책"))
                .andDo(print());
    }

    @Test
    @DisplayName("도서 단건 조회 - 실패 (존재하지 않는 ID)")
    void getBookById_Fail() throws Exception {
        // given
        Long bookId = 999L;
        // Service에서 예외를 던지도록 설정 (ControllerAdvice가 있다면 404 처리됨)
        given(bookService.findBookById(bookId)).willThrow(new RuntimeException("책을 찾을 수 없습니다."));

        // when & then
        mockMvc.perform(get("/api/admin/books/{id}", bookId))
                .andExpect(status().isNotFound()) // ControllerAdvice에서 404로 매핑한다고 가정
                .andDo(print());
    }

    @Test
    @DisplayName("도서 수정")
    void updateBook() throws Exception {
        // given
        Long bookId = 1L;
        BookUpdateRequest updateRequest = new BookUpdateRequest();
        // updateRequest 값 세팅 (필요 시)

        BookResponse updatedResponse = createMockBookResponse(bookId, "수정된 제목");

        given(bookService.updateBook(eq(bookId), any(BookUpdateRequest.class)))
                .willReturn(updatedResponse);

        // when & then
        mockMvc.perform(put("/api/admin/books/{id}", bookId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .with(csrf())
                        .content(objectMapper.writeValueAsString(updateRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("수정된 제목"))
                .andDo(print());
    }

    @Test
    @DisplayName("AI 도서 정보 검색 (ISBN)")
    void searchBookWithAi() throws Exception {
        // given
        String isbn = "9788912345678";
        ParsingDto mockParsingDto = new ParsingDto();
        mockParsingDto.setIsbn(isbn);
        mockParsingDto.setTitle("AI가 찾은 책");
        mockParsingDto.setDescription("이 책은 AI가 설명합니다...");

        given(bookRegistrationService.getBookInfoWithAi(isbn)).willReturn(mockParsingDto);

        // when & then
        mockMvc.perform(get("/api/admin/books/search-api")
                        .param("isbn", isbn))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isbn").value(isbn))
                .andExpect(jsonPath("$.title").value("AI가 찾은 책"))
                .andExpect(jsonPath("$.description").value("이 책은 AI가 설명합니다..."))
                .andDo(print());
    }
}