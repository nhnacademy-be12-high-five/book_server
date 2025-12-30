package com.nhnacademy.book_server.controller.book;

import com.nhnacademy.book_server.controller.BookLikeController;
import com.nhnacademy.book_server.dto.BookResponse;
import com.nhnacademy.book_server.entity.Book;
import com.nhnacademy.book_server.service.BookLikeService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(BookLikeController.class)
@WithMockUser // 시큐리티 통과용 가짜 사용자
class BookLikeControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private BookLikeService bookLikeService;

    @Test
    @DisplayName("좋아요 토글 (등록/취소) 테스트")
    void toggleLike() throws Exception {
        // given
        Long bookId = 1L;
        Long memberId = 100L;

        // when & then
        mockMvc.perform(post("/api/books/{bookId}/likes", bookId)
                        .header("X-USER-ID", memberId)
                        .with(csrf()) // POST 요청 필수
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());

        // verify: 서비스 메소드가 올바른 파라미터로 호출되었는지 검증
        verify(bookLikeService).toggleLike(bookId, memberId);
    }

    @Test
    @DisplayName("마이페이지 - 좋아요 누른 도서 목록 조회 테스트")
    void getMyLikedBooks() throws Exception {
        // given
        Long memberId = 100L;

        // 가짜 응답 데이터 생성 (BookResponse 내부 필드는 상황에 맞게 가정)
        // BookResponse에 기본 생성자나 Builder가 있다고 가정하고 Mocking하거나 객체 생성
        // 여기서는 Mock 객체를 리스트에 담는 방식으로 표현합니다.
        BookResponse mockResponse = new BookResponse(
                1L,                     // id
                "좋아요 한 책",           // title
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
                null,                    // aiReviewSummary
                null,
                null
        );
        given(bookLikeService.getMyLikedBooks(eq(memberId), any(Pageable.class)))
                .willReturn(List.of(mockResponse));

        // when & then
        mockMvc.perform(get("/api/books/my-page/likes")
                        .header("X-USER-ID", memberId)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.size()").value(1))
                .andExpect(jsonPath("$[0].title").value("좋아요 한 책")); // 필드명 확인 필요
    }

    @Test
    @DisplayName("좋아요 상태 확인 - 로그인 상태 (헤더 있음)")
    void getLikeStatus_LoggedIn() throws Exception {
        // given
        Long bookId = 1L;
        Long memberId = 100L;

        // 서비스가 true를 반환하도록 설정
        given(bookLikeService.isLiked(bookId, memberId)).willReturn(true);

        // when & then
        mockMvc.perform(get("/api/books/{bookId}/likes/status", bookId)
                        .header("X-USER-ID", memberId)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content().string("true")); // Body 값 확인

        verify(bookLikeService).isLiked(bookId, memberId);
    }

    @Test
    @DisplayName("좋아요 상태 확인 - 비로그인 상태 (헤더 없음)")
    void getLikeStatus_Guest() throws Exception {
        // given
        Long bookId = 1L;
        // 헤더를 보내지 않음 (memberId == null)

        // when & then
        mockMvc.perform(get("/api/books/{bookId}/likes/status", bookId)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content().string("false")); // 컨트롤러 로직에 의해 false 반환

        // verify: 헤더가 없으면 서비스 로직을 타지 않아야 함
        verify(bookLikeService, never()).isLiked(any(), any());
    }
}