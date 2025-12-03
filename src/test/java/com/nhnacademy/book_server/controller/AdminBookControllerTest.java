//package com.nhnacademy.book_server.controller;
//
//import com.fasterxml.jackson.databind.ObjectMapper;
//import com.nhnacademy.book_server.dto.BookResponse;
//import com.nhnacademy.book_server.entity.Book;
//import com.nhnacademy.book_server.entity.BookUpdateRequest;
//import com.nhnacademy.book_server.entity.Publisher;
//import com.nhnacademy.book_server.parser.ParsingDto;
//import com.nhnacademy.book_server.service.BookService;
//import com.nhnacademy.book_server.service.DataParsingService;
//import org.junit.jupiter.api.DisplayName;
//import org.junit.jupiter.api.Test;
//import org.springframework.beans.factory.annotation.Autowired;
//import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
//import org.springframework.data.domain.Page;
//import org.springframework.data.domain.PageImpl;
//import org.springframework.data.domain.PageRequest;
//import org.springframework.data.domain.Pageable;
//import org.springframework.data.web.PageableDefault;
//import org.springframework.data.web.config.PageableHandlerMethodArgumentResolverCustomizer;
//import org.springframework.http.MediaType;
//import org.springframework.security.test.context.support.WithMockUser;
//import org.springframework.test.context.bean.override.mockito.MockitoBean; // Spring Boot 3.4+
//import org.springframework.test.web.servlet.MockMvc;
//
//import java.util.List;
//import java.util.Optional;
//
//import static org.mockito.ArgumentMatchers.any;
//import static org.mockito.ArgumentMatchers.eq;
//import static org.mockito.BDDMockito.given;
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
//    @Autowired // MockMvc는 가짜 객체가 아니라 테스트 도구이므로 Autowired
//    private MockMvc mockMvc;
//
//    @Autowired // JSON 변환을 위해 Autowired
//    private ObjectMapper objectMapper;
//
//    @MockitoBean // 서비스는 가짜 객체로 대체
//    private BookService bookService;
//
//    @MockitoBean
//    private DataParsingService service;
//
//    @Autowired
//    private PageableHandlerMethodArgumentResolverCustomizer pageableCustomizer;
//
//    @Test
//    @DisplayName("도서 생성 성공")
//    @WithMockUser(roles = "ADMIN")
//    void createBook() throws Exception {
//        // given
//        ParsingDto parsingDto = new ParsingDto();
//
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
////        // 서비스가 호출되면 savedBook을 리턴한다고 가정
//        given(bookService.createBook(any(ParsingDto.class))).willReturn(savedBook);
////
////        // when & then
//        mockMvc.perform(post("/api/admin")
//                        .with(csrf()) // 사이트간 요청 위조 방지
//                        .header("X-USER-Id", "admin") // 헤더 필수
//                        .contentType(MediaType.APPLICATION_JSON)
//                        .content(objectMapper.writeValueAsString(parsingDto))) // 객체를 JSON 문자열로 변환
//                .andExpect(status().isCreated()) // 201 Created 확인
//                .andExpect(jsonPath("$.id").value(1L))
//                .andExpect(jsonPath("$.title").value("Test Book")) // 응답 JSON 확인
//                .andExpect(jsonPath("$.isbn13").value("1234567890123"))
//                .andExpect(jsonPath("$.price").value("15000"))
//                .andDo(print());
//
//
//        verify(bookService).createBook(eq(parsingDto));
//    }
//
//    @Test
//    @DisplayName("도서 전체 조회 성공")
//    @WithMockUser(roles = "ADMIN")
//    void getAllBooks() throws Exception {
//        // given
//        Book book1 = Book.builder().id(1L).title("Book 1").build();
//        Book book2 = Book.builder().id(2L).title("Book 2").build();
//
//        Pageable pageable = PageRequest.of(0, 20);
//        Page<Book> bookPage = new PageImpl<>(List.of(book1, book2), pageable, 2);
//
////        given(bookService.findAllBooks(pageable)).willReturn(bookPage);
//        given(bookService.findAllBooks(pageable)).willReturn(bookPage);
//
//        // when & then
//        mockMvc.perform(get("/api/admin")
//                        .header("X-USER-ID","admin")
//                        .contentType(MediaType.APPLICATION_JSON))
//                .andExpect(status().isOk())
//                .andExpect(jsonPath("$.size()").value(2))
//                .andExpect(jsonPath("$[0].id").value(1L))
//                .andExpect(jsonPath("$[0].title").value("Book 1"))
//                .andDo(print());
//    }
////
////    @Test
////    @DisplayName("도서 단건 조회 성공")
////    @WithMockUser(roles = "ADMIN")
////    void getBookById() throws Exception {
////        // given
////        Long bookId = 1L;
////        Book book = Book.builder().id(bookId).title("Target Book").build();
////
////        given(bookService.findBookById(eq(bookId))).willReturn(Optional.of(book));
////
////        // when & then
////        mockMvc.perform(get("/api/admin/{id}", bookId)
////                        .header("X-User-Id", "admin"))
////                .andExpect(status().isOk())
////                .andDo(print());
////    }
////
////    @Test
////    @DisplayName("도서 한권 수정")
////    @WithMockUser(roles = "ADMIN")
////    void updateBook() throws Exception {
////        Long bookId=1L;
////        String userId = "admin";
////
////        BookUpdateRequest updateDto = new BookUpdateRequest();
////        updateDto.setTitle("Updated Title");
////        updateDto.setPrice(20000);
////        updateDto.setIsbn("1234567890123");
////
////        Book updatedBook = Book.builder()
////                .id(bookId)
////                .title("Updated Title") // 수정된 제목
////                .price(20000)
////                .isbn13("1234567890123")
////                .build();
////
////        given(bookService.updateBook(eq(bookId), any(BookUpdateRequest.class)))
////                .willReturn(updatedBook);
////
////        mockMvc.perform(put("/api/admin/{id}", bookId)
////                        .with(csrf()) // [중요] PUT 요청도 CSRF 토큰 필요
////                        .header("X-User-Id", userId)
////                        .contentType(MediaType.APPLICATION_JSON)
////                        .content(objectMapper.writeValueAsString(updateDto))) // DTO를 JSON으로 변환해서 보냄
////                .andExpect(status().isOk())
////                .andExpect(jsonPath("$.title").value("Updated Title")) // 수정된 값이 오는지 확인
////                .andExpect(jsonPath("$.price").value(20000))
////                .andDo(print());
////    }
//////
////    @Test
////    @DisplayName("도서 한권 삭제")
////    @WithMockUser(roles = "ADMIN")
////    void deleteBook() throws Exception{
////
////        Long bookId=1L;
////        String userId = "admin";
////
////        mockMvc.perform(delete("/api/admin/{id}", bookId)
////                        .with(csrf()) // DELETE도 데이터 변경이므로 CSRF 필요
////                        .header("X-User-Id", userId)) // DELETE는 보통 Body(content)를 보내지 않음!
////
////                .andExpect(status().isOk()) // 혹은 isNoContent() (204) 인지 컨트롤러 구현에 따라 다름
////                .andDo(print());
////    }
//
//}