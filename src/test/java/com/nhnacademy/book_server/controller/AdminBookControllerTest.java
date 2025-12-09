//package com.nhnacademy.book_server.controller;
//
//import com.fasterxml.jackson.databind.ObjectMapper;
//import com.nhnacademy.book_server.dto.BookResponse;
//import com.nhnacademy.book_server.dto.request.BookUpdateRequest;
//import com.nhnacademy.book_server.entity.Book;
//import com.nhnacademy.book_server.parser.ParsingDto;
//import com.nhnacademy.book_server.service.BookService;
//import org.junit.jupiter.api.DisplayName;
//import org.junit.jupiter.api.Test;
//import org.springframework.beans.factory.annotation.Autowired;
//import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
//import org.springframework.data.domain.Page;
//import org.springframework.data.domain.PageImpl;
//import org.springframework.data.domain.Pageable;
//import org.springframework.http.MediaType;
//import org.springframework.security.test.context.support.WithMockUser;
//import org.springframework.test.context.bean.override.mockito.MockitoBean;
//import org.springframework.test.web.servlet.MockMvc;
//
//import java.util.List;
//
//import static org.mockito.ArgumentMatchers.*;
//import static org.mockito.BDDMockito.given;
//import static org.mockito.Mockito.doThrow;
//import static org.mockito.Mockito.verify;
//import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
//import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
//import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
//import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
//import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
//
//@WebMvcTest(AdminBookController.class)
//class AdminBookControllerTest {
//
//    @Autowired
//    private MockMvc mockMvc;
//
//    @Autowired
//    private ObjectMapper objectMapper;
//
//    @MockitoBean
//    private BookService bookService;
//
//    @Test
//    @DisplayName("도서 생성 성공")
//    @WithMockUser(roles = "ADMIN")
//    void createBook() throws Exception {
//        // given
//        ParsingDto parsingDto = new ParsingDto();
//        parsingDto.setTitle("Test Book");
//        parsingDto.setIsbn("1234567890123");
//        parsingDto.setPrice("15000");
//
//        Book savedBook = Book.builder()
//                .id(1L)
//                .title("Test Book")
//                .isbn13("1234567890123")
//                .price(15000)
//                .build();
//
//        // 서비스가 Book 엔티티를 반환
//        given(bookService.createBook(any(ParsingDto.class))).willReturn(savedBook);
//
//        // when & then
//        mockMvc.perform(post("/api/admin")
//                        .with(csrf())
//                        .header("X-User-Id", "1")
//                        .contentType(MediaType.APPLICATION_JSON)
//                        .content(objectMapper.writeValueAsString(parsingDto)))
//                .andExpect(status().isCreated())
//                .andExpect(jsonPath("$.id").value(1L))
//                .andExpect(jsonPath("$.title").value("Test Book"))
//                .andDo(print());
//    }
//
//    @Test
//    @DisplayName("도서 전체 조회 성공")
//    @WithMockUser(roles = "ADMIN")
//    void getAllBooks() throws Exception {
//        // given
//        // 1. 테스트용 Book 엔티티 생성
//        Book book1 = Book.builder().id(1L).title("Book 1").price(10000).build();
//        Book book2 = Book.builder().id(2L).title("Book 2").price(20000).build();
//
//        // 2. Service가 반환할 Page<BookResponse> 생성
//        List<BookResponse> responseList = List.of(
//                BookResponse.from(book1),
//                BookResponse.from(book2)
//        );
//        Page<BookResponse> responsePage = new PageImpl<>(responseList);
//
//        // 3. Mocking
//        given(bookService.findAllBooks(any(Pageable.class))).willReturn(responsePage);
//
//        // when & then
//        mockMvc.perform(get("/api/admin")
//                        .param("page", "0")
//                        .param("size", "10")
//                        .contentType(MediaType.APPLICATION_JSON))
//                .andExpect(status().isOk())
//                .andExpect(jsonPath("$.size()").value(2)) // List 크기 확인
//                .andExpect(jsonPath("$[0].title").value("Book 1"))
//                .andExpect(jsonPath("$[1].title").value("Book 2"))
//                .andDo(print());
//    }
//
//    @Test
//    @DisplayName("도서 단건 조회 성공")
//    @WithMockUser(roles = "ADMIN")
//    void getBookById() throws Exception {
//        // given
//        Long bookId = 1L;
//
//        Book book = Book.builder()
//                .id(bookId)
//                .title("Target Book")
//                .price(12000)
//                .isbn13("9781234567890")
//                .build();
//
//        // Controller 코드를 보면 Service가 BookResponse를 바로 리턴함
//        BookResponse response = BookResponse.from(book);
//
//        // Mocking: Service가 BookResponse를 반환
//        given(bookService.findBookById(eq(bookId))).willReturn(response);
//
//        // when & then
//        mockMvc.perform(get("/api/admin/{id}", bookId)
//                        .accept(MediaType.APPLICATION_JSON))
//                .andExpect(status().isOk())
//                .andExpect(jsonPath("$.id").value(bookId))
//                .andExpect(jsonPath("$.title").value("Target Book"))
//                .andExpect(jsonPath("$.price").value(12000))
//                .andDo(print());
//    }
//
//    @Test
//    @DisplayName("도서 한권 수정 성공")
//    @WithMockUser(roles = "ADMIN")
//    void updateBook() throws Exception {
//        // given
//        Long bookId = 1L;
//        String userId = "1";
//
//        // 요청 DTO
//        BookUpdateRequest updateDto = new BookUpdateRequest();
//        updateDto.setTitle("Updated Title");
//        updateDto.setPrice(20000);
//        updateDto.setIsbn("1234567890123");
//
//        // 서비스가 비즈니스 로직 수행 후 리턴할 '수정된 Book 엔티티'
//        Book updatedBook = Book.builder()
//                .id(bookId)
//                .title("Updated Title")
//                .price(20000)
//                .isbn13("1234567890123")
//                .build();
//
//        // Mocking: 서비스의 updateBook은 'Book' 엔티티를 반환 (Controller가 이를 받아 BookResponse로 변환함)
//        given(bookService.updateBook(eq(bookId), any(BookUpdateRequest.class)))
//                .willReturn(updatedBook);
//
//        // when & then
//        mockMvc.perform(put("/api/admin/{id}", bookId)
//                        .with(csrf())
//                        .header("X-User-Id", userId)
//                        .contentType(MediaType.APPLICATION_JSON)
//                        .content(objectMapper.writeValueAsString(updateDto)))
//                .andExpect(status().isOk())
//                .andExpect(jsonPath("$.title").value("Updated Title")) // 응답은 BookResponse
//                .andExpect(jsonPath("$.price").value(20000))
//                .andDo(print());
//    }
//
//    @Test
//    @DisplayName("도서 삭제 실패 - 책 없음")
//    @WithMockUser(roles = "ADMIN")
//    void deleteBook_NotFound() throws Exception {
//        // given
//        Long bookId = 99L;
//        String userId = "1";
//
//        // Service가 RuntimeException을 던지도록 설정
//        doThrow(new RuntimeException("책을 찾을 수 없습니다."))
//                .when(bookService).deleteBook(bookId, 1L);
//
//        // when & then
//        mockMvc.perform(delete("/api/admin/{id}", bookId)
//                        .with(csrf())
//                        .header("X-User-Id", userId))
//                .andExpect(status().isNotFound()) // Controller catch 블록에서 404 리턴
//                .andDo(print());
//    }
//}