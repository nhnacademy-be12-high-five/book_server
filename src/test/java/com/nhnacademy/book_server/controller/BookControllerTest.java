//package com.nhnacademy.book_server.controller;
//
//import com.fasterxml.jackson.databind.ObjectMapper;
//import com.nhnacademy.book_server.entity.Book;
//import com.nhnacademy.book_server.service.BookService;
//import org.junit.jupiter.api.Test;
//import org.springframework.beans.factory.annotation.Autowired;
//import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
//
//import org.springframework.http.MediaType;
//import org.springframework.test.context.bean.override.mockito.MockitoBean;
//import org.springframework.test.web.servlet.MockMvc;
//import static org.mockito.ArgumentMatchers.any;
//import java.sql.Date;
//import java.util.Arrays;
//import java.util.List;
//
//import static org.mockito.Mockito.when;
//import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
//import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
//import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
//import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
//
//@WebMvcTest(AdminBookController.class)
//public class BookControllerTest {
//    @Autowired
//    private MockMvc mockMvc;
//
//    private final String TEST_USER_ID = "admin123";
//    @MockitoBean
//    private BookService bookService;
//
//    @Test
//    void createBookTest() throws Exception {
//        // 1. GIVEN: 테스트에 필요한 객체 설정
//        Book inputBook = new Book(null, "1234567", "1", "제목", "저자", "테스트출판사", Date.valueOf("2020-12-12"), "Edition",
//                12000.5, "image url", "content",1, "테스트 부제목", "기여자", "2020-12-12", true, true);
//        Book savedBook = new Book(1L, "1234567", "1", "제목", "저자", "테스트출판사", Date.valueOf("2020-12-12"), "Edition",
//                12000.12, "image url", "content", 1, "테스트 부제목", "기여자", "2020-12-12", true, true);
//
////        // 2. WHEN: Mock 설정
////        // TEST_USER_ID 대신 any(String.class)를 사용하면 userId 값에 상관없이 Mock을 사용할 수 있습니다.
//        when(bookService.createBook(any(Book.class), any(String.class))).thenReturn(savedBook);
////
//        ObjectMapper objectMapper = new ObjectMapper();
//        String bookJson = objectMapper.writeValueAsString(inputBook);
//
//        // 3. THEN: MockMvc 실행 및 검증 (주석 해제 및 검증 추가)
//        mockMvc.perform(post("/api/admin")
//                        .contentType(MediaType.APPLICATION_JSON) // MediaType import 필요
//                        .content(bookJson)
//                        .header("X-User-Id", TEST_USER_ID)
//                )
//                .andExpect(status().isCreated()) // HTTP 201 Created 검증
//                .andExpect(jsonPath("$.id").value(1L)) // 응답 JSON의 ID 필드 검증
//                .andExpect(jsonPath("$.title").value("제목")); // 응답 JSON의 Title 필드 검증
//    }
//
//    @Test
//    void findAllBooks(String userId) throws Exception {
//
//        Book book1 = new Book(1L, "1111", "1", "자바의 정석", "남궁성", "도우출판",
//                Date.valueOf("2019-01-01"), "1판", 25000.5,
//                "url1", "내용1", 1, "부제1", "기여자1", "2019-01-01", true, true);
//
//        // 두 번째 더미 도서
//        Book book2 = new Book(2L, "2222", "1", "스프링 부트", "저자2", "출판사2",
//                Date.valueOf("2020-02-02"), "2판", 35000.5,
//                "url2", "내용2", 2, "부제2", "기여자2", "2020-02-02", true, true);
//
//        List<Book> mockBooks = Arrays.asList(book1, book2);
//
//        when(bookService.findAllBooks(any(String.class))).thenReturn(mockBooks);
//
//        // 3. THEN: MockMvc 실행 및 검증
//        mockMvc.perform(get("/api/admin")
//                        // AdminBookController의 getAllBooks(String userId) 메서드는 userId를 요청 파라미터로 받습니다.
//                        // 컨트롤러 메서드 시그니처: public ResponseEntity<List<Book>> getAllBooks(String userId)
//                        // 실제 요청: GET /api/admin?userId=admin123
//                        .param("userId", TEST_USER_ID)
//                        .header("X-User-Id", TEST_USER_ID) // 혹시 모를 검증을 위해 헤더도 포함
//                        .contentType(MediaType.APPLICATION_JSON)
//                )
//                .andExpect(status().isOk()) // HTTP 200 OK인지 검증
//                .andExpect(jsonPath("$").isArray()) // 응답 본문이 JSON 배열인지 검증
//                .andExpect(jsonPath("$.length()").value(2)) // 배열의 길이가 2인지 검증
//                .andExpect(jsonPath("$[0].title").value("자바의 정석")) // 첫 번째 책의 제목 검증
//                .andExpect(jsonPath("$[1].isbn").value("2222")); // 두 번째 책의 ISBN 검증
//
//    }
//}
