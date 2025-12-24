//package com.nhnacademy.book_server.controller.book;
//
//import com.nhnacademy.book_server.controller.BookLikeController;
//import com.nhnacademy.book_server.dto.BookResponse;
//import com.nhnacademy.book_server.service.BookLikeService;
//import org.junit.jupiter.api.DisplayName;
//import org.junit.jupiter.api.Test;
//import org.mockito.Mock;
//import org.springframework.beans.factory.annotation.Autowired;
//import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
//import org.springframework.data.domain.Pageable;
//import org.springframework.http.MediaType;
//import org.springframework.test.web.servlet.MockMvc;
//
//import java.util.List;
//
//import static org.mockito.ArgumentMatchers.any;
//import static org.mockito.ArgumentMatchers.eq;
//import static org.mockito.BDDMockito.given;
//import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
//import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
//import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
//import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
//import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
//import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
//
//@WebMvcTest(BookLikeController.class)
//class BookLikeControllerTest {
//
//    @Autowired
//    private MockMvc mockMvc;
//
//    @Mock
//    private BookLikeService bookLikeService;
//
//    @Test
//    @DisplayName("좋아요 토글 API 테스트")
//    void toggleLikeTest() throws Exception {
//        // given
//        Long bookId = 1L;
//        Long memberId = 100L;
//
//        // when & then
//        mockMvc.perform(post("/api/books/{bookId}/likes", bookId)
//                        .header("X-USER-ID", memberId) // 헤더 필수
//                        .with(csrf()) // POST 요청 시 CSRF 토큰 필요
//                        .contentType(MediaType.APPLICATION_JSON))
//                .andDo(print())
//                .andExpect(status().isOk());
//    }
//
//    @Test
//    @DisplayName("좋아요 목록 조회 API 테스트")
//    void getMyLikedBooksTest() throws Exception {
//        // given
//        Long memberId = 100L;
//        BookResponse response = BookResponse.builder() // BookResponse에 @Builder가 있다고 가정
//                .bookId(1L)
//                .title("좋아요한 책")
//                .build();
//
//        given(bookLikeService.getMyLikedBooks(eq(memberId), any(Pageable.class)))
//                .willReturn(List.of(response));
//
//        // when & then
//        mockMvc.perform(get("/api/books/my-page/likes")
//                        .header("X-USER-ID", memberId)
//                        .param("page", "0")
//                        .param("size", "10")
//                        .contentType(MediaType.APPLICATION_JSON))
//                .andDo(print())
//                .andExpect(status().isOk())
//                .andExpect(jsonPath("$[0].title").value("좋아요한 책"));
//    }
//
//    @Test
//    @DisplayName("좋아요 상태 조회 API (로그인 상태)")
//    void getLikeStatus_LoggedIn() throws Exception {
//        // given
//        Long bookId = 1L;
//        Long memberId = 100L;
//
//        given(bookLikeService.isLiked(bookId, memberId)).willReturn(true);
//
//        // when & then
//        mockMvc.perform(get("/api/books/{bookId}/likes/status", bookId)
//                        .header("X-USER-ID", memberId)
//                        .contentType(MediaType.APPLICATION_JSON))
//                .andDo(print())
//                .andExpect(status().isOk())
//                .andExpect(jsonPath("$").value(true));
//    }
//
//    @Test
//    @DisplayName("좋아요 상태 조회 API (비로그인 상태 - 헤더 없음)")
//    void getLikeStatus_NotLoggedIn() throws Exception {
//        // given
//        Long bookId = 1L;
//        // 헤더를 보내지 않음
//
//        // when & then
//        mockMvc.perform(get("/api/books/{bookId}/likes/status", bookId)
//                        .contentType(MediaType.APPLICATION_JSON))
//                .andDo(print())
//                .andExpect(status().isOk())
//                .andExpect(jsonPath("$").value(false)); // Controller 로직상 false 반환
//    }
//}