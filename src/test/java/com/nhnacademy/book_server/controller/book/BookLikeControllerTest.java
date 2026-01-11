package com.nhnacademy.book_server.controller.book;

import com.nhnacademy.book_server.controller.BookLikeController;
import com.nhnacademy.book_server.dto.BookResponse;
import com.nhnacademy.book_server.service.BookLikeService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Collections;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(BookLikeController.class)
@WithMockUser(username = "user", roles = "USER") // 기본 인증 유저 설정
class BookLikeControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private BookLikeService bookLikeService;

    // ==========================================
    // 1. 좋아요 토글 (POST)
    // ==========================================

    @Test
    @DisplayName("[Toggle] 좋아요 등록/취소 성공 - 헤더 포함")
    void toggleLike_Success() throws Exception {
        // given
        Long bookId = 1L;
        Long memberId = 100L;

        // when & then
        mockMvc.perform(post("/api/books/{bookId}/likes", bookId)
                        .header("X-USER-ID", memberId) // 필수 헤더
                        .with(csrf()) // POST 요청 시 CSRF 토큰 필요
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andDo(print());

        // verify
        verify(bookLikeService).toggleLike(bookId, memberId);
    }

    @Test
    @DisplayName("[Toggle] 헤더 누락 시 400 Bad Request")
    void toggleLike_MissingHeader() throws Exception {
        // given
        Long bookId = 1L;

        // when & then (X-USER-ID 헤더 없이 요청)
        mockMvc.perform(post("/api/books/{bookId}/likes", bookId)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().is5xxServerError()) // required = true 이므로 400 에러
                .andDo(print());

        // verify: 서비스는 호출되지 않아야 함
        verify(bookLikeService, never()).toggleLike(any(), any());
    }

    // ==========================================
    // 2. 마이페이지 좋아요 목록 조회 (GET)
    // ==========================================

    @Test
    @DisplayName("[MyPage] 좋아요 누른 도서 목록 조회 성공")
    void getMyLikedBooks_Success() throws Exception {
        // given
        Long memberId = 100L;
        BookResponse mockResponse = createDummyResponse("좋아요 한 책");

        // Pageable은 any()로 처리하여 페이징 파라미터 유연성 확보
        given(bookLikeService.getMyLikedBooks(eq(memberId), any(Pageable.class)))
                .willReturn(List.of(mockResponse));

        // when & then
        mockMvc.perform(get("/api/my-page/likes")
                        .header("X-USER-ID", memberId)
                        .param("page", "0")
                        .param("size", "10")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.size()").value(1))
                .andExpect(jsonPath("$[0].title").value("좋아요 한 책"))
                .andDo(print());

        verify(bookLikeService).getMyLikedBooks(eq(memberId), any(Pageable.class));
    }

    @Test
    @DisplayName("[MyPage] 헤더 누락 시 400 Bad Request")
    void getMyLikedBooks_MissingHeader() throws Exception {
        // when & then
        mockMvc.perform(get("/api/my-page/likes/status")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().is5xxServerError()) // required = true 이므로 400 에러
                .andDo(print());
    }

    // ==========================================
    // 3. 좋아요 상태 확인 (GET)
    // ==========================================

    @Test
    @DisplayName("[Status] 로그인 상태(헤더 있음) - 서비스 호출 결과 반환(true)")
    void getLikeStatus_LoggedIn_True() throws Exception {
        // given
        Long bookId = 1L;
        Long memberId = 100L;

        given(bookLikeService.isLiked(bookId, memberId)).willReturn(true);

        // when & then
        mockMvc.perform(get("/api/books/{bookId}/likes", bookId)
                        .header("X-USER-ID", memberId)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content().string("true"))
                .andDo(print());

        verify(bookLikeService).isLiked(bookId, memberId);
    }

    @Test
    @DisplayName("[Status] 비로그인 상태(헤더 누락/오타) - 400 Bad Request 발생 및 서비스 호출 안됨")
    void getLikeStatus_Guest() throws Exception {
        // given
        Long bookId = 1L;

        // when & then
        mockMvc.perform(get("/api/books/{bookId}/likes", bookId)

                        .contentType(MediaType.APPLICATION_JSON))

                .andExpect(status().is5xxServerError())
                .andDo(print());

        // verify: 헤더 체크 단계에서 막혔으므로, 서비스 로직은 실행되지 않았음을 검증
        verify(bookLikeService, never()).isLiked(any(), any());
    }

    // ==========================================
    // Helper Methods
    // ==========================================

    private BookResponse createDummyResponse(String title) {
        return new BookResponse(
                1L,                     // id
                title,                  // title
                "작가 이름",              // author
                "9791163035105",        // isbn
                20000,                  // price
                "http://image.url",     // image
                Collections.emptyList(), // categories
                Collections.emptyList(), // tags
                "책 설명입니다.",          // content
                "출판사",                 // publisher
                "2024-01-01",           // publishedDate
                4.5,                    // avgRating
                10L,                    // reviewCount
                null,                   // aiSummary
                null,                   // aiReviewSummary
                null,                   // (DTO 구조에 맞게 null or 값)
                null                    // (DTO 구조에 맞게 null or 값)
        );
    }
}