package com.nhnacademy.book_server.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nhnacademy.book_server.dto.BookResponse;
import com.nhnacademy.book_server.dto.request.BookUpdateRequest;
import com.nhnacademy.book_server.entity.Book;
import com.nhnacademy.book_server.parser.ParsingDto;
import com.nhnacademy.book_server.service.BookService;
import com.nhnacademy.book_server.service.DataParsingService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.config.PageableHandlerMethodArgumentResolverCustomizer;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean; // Spring Boot 3.4+
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AdminBookController.class)
class AdminBookControllerTest {

    @Autowired // MockMvc는 가짜 객체가 아니라 테스트 도구이므로 Autowired
    private MockMvc mockMvc;

    @Autowired // JSON 변환을 위해 Autowired
    private ObjectMapper objectMapper;

    @MockitoBean // 서비스는 가짜 객체로 대체
    private BookService bookService;

    @MockitoBean
    private DataParsingService service;

    @Autowired
    private PageableHandlerMethodArgumentResolverCustomizer pageableCustomizer;

    @Test
    @DisplayName("도서 생성 성공")
    @WithMockUser(roles = "ADMIN")
    void createBook() throws Exception {
        // given
        ParsingDto parsingDto = new ParsingDto();

        parsingDto.setTitle("Test Book");
        parsingDto.setIsbn("1234567890123");
        parsingDto.setPrice("15000");

        Book savedBook = Book.builder()
                .id(1L)
                .title("Test Book")
                .isbn13("1234567890123")
                .price(15000)
                .build();

//        // 서비스가 호출되면 savedBook을 리턴한다고 가정
        given(bookService.createBook(any(ParsingDto.class))).willReturn(savedBook);
//
//        // when & then
        mockMvc.perform(post("/api/admin")
                        .with(csrf()) // 사이트간 요청 위조 방지
                        .header("X-USER-Id", 1) // 헤더 필수
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(parsingDto))) // 객체를 JSON 문자열로 변환
                .andExpect(status().isCreated()) // 201 Created 확인
                .andExpect(jsonPath("$.id").value("1"))
                .andExpect(jsonPath("$.title").value("Test Book")) // 응답 JSON 확인
                .andExpect(jsonPath("$.isbn13").value("1234567890123"))
                .andExpect(jsonPath("$.price").value(15000))
                .andDo(print());


        verify(bookService).createBook(eq(parsingDto));
    }

    @Test
    @DisplayName("도서 전체 조회 성공")
    @WithMockUser(roles = "ADMIN")
    void getAllBooks() throws Exception {

        // given
        // 1. 테스트용 Book 엔티티 생성
        Book book1 = Book.builder().id(1L).title("Book 1").price(10000).build();
        Book book2 = Book.builder().id(2L).title("Book 2").price(20000).build();

        // 2. Service가 반환할 Page<BookResponse> 데이터 생성
        // (실제 Service 로직처럼 Book을 BookResponse로 변환해서 리스트에 담습니다)
        List<BookResponse> responseList = List.of(
                BookResponse.from(book1),
                BookResponse.from(book2)
        );

        Page<BookResponse> responsePage = new PageImpl<>(responseList);

        // 3. Mocking: Service가 호출되면 위에서 만든 responsePage를 리턴하도록 설정
        given(bookService.findAllBooks(any(Pageable.class))).willReturn(responsePage);

        // when & then
        mockMvc.perform(get("/api/admin")
                        .header("X-USER-ID", "1") // [중요] Long 파싱 가능한 숫자 문자열
                        .param("page", "0")       // 페이징 파라미터 추가 (선택)
                        .param("size", "10")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.size()").value(2)) // 반환된 리스트 개수 확인
                .andExpect(jsonPath("$[0].id").value(1L))
                .andExpect(jsonPath("$[0].title").value("Book 1"))
                .andDo(print());
    }

    @Test
    @DisplayName("도서 단건 조회 성공")
    @WithMockUser(roles = "ADMIN")
    void getBookById() throws Exception {
        // given
        Long bookId = 1L;

        // 1. Service가 반환할 Book 엔티티 생성
        // (BookResponse 변환 시 NPE가 나지 않도록 필요한 필드를 채워줍니다)
        Book book = Book.builder()
                .id(bookId)
                .title("Target Book")
                .price(12000)
                .isbn13("9781234567890")
                .build();

        // 2. Mocking: Service는 Optional<Book>을 반환함
        given(bookService.findBookById(eq(bookId))).willReturn(Optional.of(book));

        // when & then
        mockMvc.perform(get("/api/admin/{id}", bookId)
                        .header("X-User-Id", "1") // [핵심 수정] "admin" -> "1" (Long 파싱 에러 방지)
                        .contentType(MediaType.APPLICATION_JSON)) // GET 요청이라 필수는 아니지만 명시 권장
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(bookId))
                .andExpect(jsonPath("$.title").value("Target Book"))
                .andExpect(jsonPath("$.price").value(12000))
                .andDo(print());
    }

    @Test
    @DisplayName("도서 한권 수정")
    @WithMockUser(roles = "ADMIN")
    void updateBook() throws Exception {
        // given
        Long bookId = 1L;
        String userId = "1";

        BookUpdateRequest updateDto = new BookUpdateRequest();

        updateDto.setTitle("Updated Title");
        updateDto.setPrice(20000);
        updateDto.setIsbn("1234567890123");

        // Service가 리턴할 수정된 Book 객체
        Book updatedBook = Book.builder()
                .id(bookId)
                .title("Updated Title") // 수정된 제목 반영
                .price(20000)
                .isbn13("1234567890123")
                .build();

        // setup 단계에서는 any()로 유연하게 설정
        given(bookService.updateBook(eq(bookId), any(BookUpdateRequest.class)))
                .willReturn(updatedBook);

        // when & then
        mockMvc.perform(put("/api/admin/{id}", bookId)
                        .with(csrf()) // PUT 요청 필수
                        .header("X-User-Id", userId) // "1" 전송
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateDto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Updated Title")) // 응답 DTO 확인
                .andExpect(jsonPath("$.price").value(20000))
                .andDo(print());

        // [수정 2] 검증 단계에서 refEq를 사용하여, 내가 보낸 DTO 값이 서비스까지 변질되지 않고 잘 도착했는지 확인
        verify(bookService).updateBook(eq(bookId), refEq(updateDto));
    }

    //

    /// /
    @Test
    @DisplayName("도서 삭제 실패 - 책 없음")
    @WithMockUser(roles = "ADMIN")
    void deleteBook_NotFound() throws Exception {
        // given
        Long bookId = 99L;
        String userId = "1";

        // Service가 예외를 던지도록 설정 (void 메서드는 doThrow 사용)
        doThrow(new RuntimeException("삭제할 아이디가 없습니다."))
                .when(bookService).deleteBook(bookId, 1L);

        // when & then
        mockMvc.perform(delete("/api/admin/{id}", bookId)
                        .with(csrf())
                        .header("X-User-Id", userId))
                .andExpect(status().isNotFound()) // 404 Not Found 확인
                .andDo(print());
    }
}

