package com.nhnacademy.book_server.controller.review;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nhnacademy.book_server.dto.request.ReviewCreateRequest;
import com.nhnacademy.book_server.dto.request.ReviewUpdateRequest;
import com.nhnacademy.book_server.dto.response.BookReviewResponse;
import com.nhnacademy.book_server.dto.response.MyPageReviewResponse;
import com.nhnacademy.book_server.dto.response.ReviewCreateResponse;
import com.nhnacademy.book_server.dto.response.UpdateReviewResponse;
import com.nhnacademy.book_server.service.review.ReviewService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.mapping.JpaMetamodelMappingContext;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ReviewController.class)
@AutoConfigureMockMvc(addFilters = false) // 핵심: 401, 403 에러 해결을 위해 시큐리티 필터 비활성화
class ReviewControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    // Spring Boot 3.4+ / Framework 6.2+ 표준 Mock 객체 주입
    @MockitoBean
    private ReviewService reviewService;

    // JPA Auditing 등 JPA 관련 빈이 로드되면서 발생하는 에러 방지
    @MockitoBean
    private JpaMetamodelMappingContext jpaMetamodelMappingContext;

    private final Long testBookId = 1L;
    private final Long testMemberId = 100L;
    private final Long testReviewId = 10L;

    @Test
    @DisplayName("리뷰 작성 성공 (이미지 포함) - 201 Created")
    void createReviewWithImage() throws Exception {
        // given
        ReviewCreateRequest requestDto = new ReviewCreateRequest(5, "정말 좋은 책입니다! 추천해요.");
        ReviewCreateResponse responseDto = new ReviewCreateResponse(testReviewId, 5, "정말 좋은 책입니다! 추천해요.");

        // JSON Request Part
        MockMultipartFile requestPart = new MockMultipartFile(
                "request",
                "",
                "application/json",
                objectMapper.writeValueAsString(requestDto).getBytes(StandardCharsets.UTF_8)
        );

        // Image Part
        MockMultipartFile imagePart = new MockMultipartFile(
                "images",
                "test.jpg",
                "image/jpeg",
                "image-data".getBytes()
        );

        given(reviewService.saveReview(any(ReviewCreateRequest.class), eq(testBookId), eq(testMemberId), anyList()))
                .willReturn(responseDto);

        // when
        ResultActions result = mockMvc.perform(multipart("/api/books/{book-id}/reviews", testBookId)
                .file(requestPart)
                .file(imagePart)
                .header("x-user-id", testMemberId) // 헤더 필수
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .accept(MediaType.APPLICATION_JSON));

        // then
        result.andExpect(status().isCreated())
                .andExpect(jsonPath("$.rating").value(5))
                .andExpect(jsonPath("$.content").value("정말 좋은 책입니다! 추천해요."))
                .andDo(print());
    }

    @Test
    @DisplayName("리뷰 작성 성공 (이미지 없음) - 201 Created")
    void createReviewWithoutImage() throws Exception {
        // given
        ReviewCreateRequest requestDto = new ReviewCreateRequest(4, "이미지 없이 작성하는 리뷰");
        ReviewCreateResponse responseDto = new ReviewCreateResponse(11L, 4, "이미지 없이 작성하는 리뷰");

        MockMultipartFile requestPart = new MockMultipartFile(
                "request",
                "",
                "application/json",
                objectMapper.writeValueAsString(requestDto).getBytes(StandardCharsets.UTF_8)
        );

        // 이미지가 null일 때 서비스 호출 매칭 주의 (isNull())
        given(reviewService.saveReview(any(ReviewCreateRequest.class), eq(testBookId), eq(testMemberId), isNull()))
                .willReturn(responseDto);

        // when
        ResultActions result = mockMvc.perform(multipart("/api/books/{book-id}/reviews", testBookId)
                .file(requestPart)
                .header("x-user-id", testMemberId)
                .contentType(MediaType.MULTIPART_FORM_DATA));

        // then
        result.andExpect(status().isCreated())
                .andExpect(jsonPath("$.rating").value(4))
                .andDo(print());
    }

    @Test
    @DisplayName("책 리뷰 리스트 조회 - 200 OK")
    void getReviews() throws Exception {
        // given
        BookReviewResponse reviewResponse = new BookReviewResponse(
                testReviewId, testMemberId, "tester", "Content", 5,
                Timestamp.valueOf(LocalDateTime.now()),
                Collections.emptyList(), 0, false
        );
        Page<BookReviewResponse> pageResponse = new PageImpl<>(List.of(reviewResponse));

        given(reviewService.getReviewList(eq(testBookId), any(Pageable.class), eq(testMemberId)))
                .willReturn(pageResponse);

        // when
        ResultActions result = mockMvc.perform(get("/api/books/{book-id}/reviews", testBookId)
                .header("x-user-id", testMemberId)
                .param("page", "0")
                .param("size", "10"));

        // then
        result.andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].reviewId").value(testReviewId))
                .andDo(print());

        verify(reviewService).getReviewList(eq(testBookId), any(Pageable.class), eq(testMemberId));
    }

    @Test
    @DisplayName("나의 리뷰 단건 조회 (데이터 있음) - 200 OK")
    void getMyReviewFound() throws Exception {
        // given
        BookReviewResponse response = new BookReviewResponse(
                testReviewId, testMemberId, "me", "My Review", 5,
                Timestamp.valueOf(LocalDateTime.now()),
                Collections.emptyList(), 0, null
        );

        given(reviewService.getMyReview(testBookId, testMemberId)).willReturn(response);

        // when
        ResultActions result = mockMvc.perform(get("/api/books/{book-id}/reviews/me", testBookId)
                .header("x-user-id", testMemberId));

        // then
        result.andExpect(status().isOk())
                .andExpect(jsonPath("$.content").value("My Review"))
                .andDo(print());
    }

    @Test
    @DisplayName("나의 리뷰 단건 조회 (데이터 없음) - 204 No Content")
    void getMyReviewNotFound() throws Exception {
        // given
        given(reviewService.getMyReview(testBookId, testMemberId)).willReturn(null);

        // when
        ResultActions result = mockMvc.perform(get("/api/books/{book-id}/reviews/me", testBookId)
                .header("x-user-id", testMemberId));

        // then
        result.andExpect(status().isNoContent())
                .andDo(print());
    }

    @Test
    @DisplayName("마이페이지 내 리뷰 리스트 조회 - 200 OK")
    void getMyReviews() throws Exception {
        // given
        MyPageReviewResponse myPageResponse = new MyPageReviewResponse(
                50L, testBookId, "Book Title", Timestamp.valueOf(LocalDateTime.now())
        );
        Page<MyPageReviewResponse> page = new PageImpl<>(List.of(myPageResponse));

        given(reviewService.getMyReviewList(eq(testMemberId), any(Pageable.class))).willReturn(page);

        // when
        ResultActions result = mockMvc.perform(get("/api/books/members/me/reviews")
                .header("x-user-id", testMemberId));

        // then
        result.andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].reviewId").value(50L))
                .andDo(print());
    }

    @Test
    @DisplayName("리뷰 수정 - 200 OK")
    void updateReview() throws Exception {
        // given
        ReviewUpdateRequest updateRequest = new ReviewUpdateRequest("Updated Content", 3, Collections.emptyList());
        UpdateReviewResponse updateResponse = new UpdateReviewResponse("Updated Content", 3);

        MockMultipartFile requestPart = new MockMultipartFile(
                "request",
                "",
                "application/json",
                objectMapper.writeValueAsString(updateRequest).getBytes(StandardCharsets.UTF_8)
        );

        given(reviewService.updateReview(any(ReviewUpdateRequest.class), eq(testBookId), eq(testReviewId), eq(testMemberId), any()))
                .willReturn(updateResponse);

        // when
        // POST 메서드로 멀티파트 요청 전송
        ResultActions result = mockMvc.perform(multipart("/api/books/{book-id}/reviews/{review-id}", testBookId, testReviewId)
                .file(requestPart)
                .header("x-user-id", testMemberId)
                .contentType(MediaType.MULTIPART_FORM_DATA));

        // then
        result.andExpect(status().isOk())
                .andExpect(jsonPath("$.content").value("Updated Content"))
                .andDo(print());
    }

    @Test
    @DisplayName("리뷰 좋아요 토글 - 200 OK")
    void toggleLike() throws Exception {
        // given
        given(reviewService.toggleReviewLike(testReviewId, testMemberId, testBookId)).willReturn(true);

        // when
        ResultActions result = mockMvc.perform(post("/api/books/{book-id}/reviews/{review-id}/like", testBookId, testReviewId)
                .header("x-user-id", testMemberId));

        // then
        result.andExpect(status().isOk())
                .andExpect(content().string("true"))
                .andDo(print());
    }
}