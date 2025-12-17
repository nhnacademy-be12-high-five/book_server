package com.nhnacademy.book_server.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nhnacademy.book_server.controller.review.ReviewController;
import com.nhnacademy.book_server.dto.request.ReviewCreateRequest;
import com.nhnacademy.book_server.dto.request.ReviewUpdateRequest;
import com.nhnacademy.book_server.dto.response.*;
import com.nhnacademy.book_server.service.ReviewService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

// properties 설정을 통해 테스트 시 Config Server 연결을 시도하지 않도록 차단합니다.
@WebMvcTest(
        value = ReviewController.class,
        properties = {
                "spring.cloud.config.enabled=false"
        }
)
@AutoConfigureMockMvc(addFilters = false) // Security Filter Chain을 건너뛰어 401/403 오류 방지
class ReviewControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ReviewService reviewService;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @DisplayName("리뷰 작성 성공")
    void createReview() throws Exception {
        // given
        Long bookId = 1L;
        Long memberId = 100L;
        ReviewCreateRequest request = new ReviewCreateRequest(5, "정말 좋은 책입니다.");
        ReviewCreateResponse response = new ReviewCreateResponse(1L, 5, "정말 좋은 책입니다.");

        MockMultipartFile requestPart = new MockMultipartFile("request", "", "application/json", objectMapper.writeValueAsString(request).getBytes(StandardCharsets.UTF_8));
        // 이미지 파일은 선택 사항이지만 테스트 커버리지를 위해 포함
        MockMultipartFile imagePart = new MockMultipartFile("images", "test.jpg", "image/jpeg", "image data".getBytes());

        given(reviewService.saveReview(any(ReviewCreateRequest.class), eq(bookId), eq(memberId), anyList()))
                .willReturn(response);

        // when & then
        mockMvc.perform(multipart("/api/books/{book-id}/reviews", bookId)
                        .file(requestPart)
                        .file(imagePart)
                        .header("x-user-id", memberId)
                        .contentType(MediaType.MULTIPART_FORM_DATA))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.reviewId").value(1L))
                .andDo(print());
    }

    @Test
    @DisplayName("리뷰 리스트 조회 성공")
    void getReviews() throws Exception {
        // given
        Long bookId = 1L;
        Long memberId = 100L;
        Pageable pageable = PageRequest.of(0, 10);
        List<BookReviewResponse> reviews = List.of(new BookReviewResponse(1L, memberId, "user", "content", 5, null, List.of(), 0, false));
        Page<BookReviewResponse> responsePage = new PageImpl<>(reviews, pageable, 1);

        given(reviewService.getReviewList(eq(bookId), any(Pageable.class), eq(memberId)))
                .willReturn(responsePage);

        // when & then
        mockMvc.perform(get("/api/books/{book-id}/reviews", bookId)
                        .header("x-user-id", memberId)
                        .param("page", "0")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].reviewId").value(1L))
                .andDo(print());
    }

    @Test
    @DisplayName("내 리뷰 단건 조회 성공")
    void getMyReview() throws Exception {
        // given
        Long bookId = 1L;
        Long memberId = 100L;
        BookReviewResponse response = new BookReviewResponse(1L, memberId, "user", "content", 5, null, List.of(), 0, null);

        given(reviewService.getMyReview(bookId, memberId)).willReturn(response);

        // when & then
        mockMvc.perform(get("/api/books/{book-id}/reviews/me", bookId)
                        .header("x-user-id", memberId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reviewId").value(1L))
                .andDo(print());
    }

    @Test
    @DisplayName("내 리뷰 단건 조회 - 없음(204 No Content)")
    void getMyReview_NotFound() throws Exception {
        // given
        Long bookId = 1L;
        Long memberId = 100L;

        given(reviewService.getMyReview(bookId, memberId)).willReturn(null);

        // when & then
        mockMvc.perform(get("/api/books/{book-id}/reviews/me", bookId)
                        .header("x-user-id", memberId))
                .andExpect(status().isNoContent())
                .andDo(print());
    }

    @Test
    @DisplayName("마이페이지 내 리뷰 리스트 조회")
    void getMyReviews() throws Exception {
        // given
        Long memberId = 100L;
        Pageable pageable = PageRequest.of(0, 10);
        List<MyPageReviewResponse> reviews = List.of(new MyPageReviewResponse(1L, 1L, "title", null));
        Page<MyPageReviewResponse> responsePage = new PageImpl<>(reviews, pageable, 1);

        given(reviewService.getMyReviewList(eq(memberId), any(Pageable.class))).willReturn(responsePage);

        // when & then
        mockMvc.perform(get("/api/books/members/me/reviews")
                        .header("x-user-id", memberId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].reviewId").value(1L))
                .andDo(print());
    }

    @Test
    @DisplayName("리뷰 수정 성공")
    void updateReview() throws Exception {
        // given
        Long bookId = 1L;
        Long reviewId = 1L;
        Long memberId = 100L;
        ReviewUpdateRequest request = new ReviewUpdateRequest("updated content", 4, Collections.emptyList());
        UpdateReviewResponse response = new UpdateReviewResponse("updated content", 4);

        MockMultipartFile requestPart = new MockMultipartFile("request", "", "application/json", objectMapper.writeValueAsString(request).getBytes(StandardCharsets.UTF_8));

        given(reviewService.updateReview(any(ReviewUpdateRequest.class), eq(bookId), eq(reviewId), eq(memberId), any()))
                .willReturn(response);

        // when & then
        mockMvc.perform(multipart("/api/books/{book-id}/reviews/{review-id}", bookId, reviewId)
                        .file(requestPart)
                        .header("x-user-id", memberId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").value("updated content"))
                .andDo(print());
    }

    @Test
    @DisplayName("좋아요 토글 성공")
    void toggleLike() throws Exception {
        // given
        Long bookId = 1L;
        Long reviewId = 1L;
        Long memberId = 100L;

        given(reviewService.toggleReviewLike(reviewId, memberId, bookId)).willReturn(true);

        // when & then
        mockMvc.perform(post("/api/books/{book-id}/reviews/{review-id}/like", bookId, reviewId)
                        .header("x-user-id", memberId))
                .andExpect(status().isOk())
                .andExpect(content().string("true"))
                .andDo(print());
    }
}