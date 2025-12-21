package com.nhnacademy.book_server.dto.review;

import com.nhnacademy.book_server.dto.event.ReviewCreatedEvent;
import com.nhnacademy.book_server.dto.event.ReviewImageDeleteEvent;
import com.nhnacademy.book_server.dto.event.ReviewImageUploadEvent;
import com.nhnacademy.book_server.dto.request.PointEarnRequest;
import com.nhnacademy.book_server.dto.request.ReviewCreateRequest;
import com.nhnacademy.book_server.dto.request.ReviewUpdateRequest;
import com.nhnacademy.book_server.dto.response.*;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class ReviewDtoTest {

    private Validator validator;

    @BeforeEach
    void setUp() {
        ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    // --- 1. Request DTO Validation Tests ---

    @Test
    @DisplayName("ReviewCreateRequest: 유효성 검사 성공")
    void reviewCreateRequestValid() {
        ReviewCreateRequest request = new ReviewCreateRequest(5, "이 책은 정말 재미있습니다. 추천합니다.");
        Set<ConstraintViolation<ReviewCreateRequest>> violations = validator.validate(request);
        assertThat(violations).isEmpty();
    }

    @Test
    @DisplayName("ReviewCreateRequest: 실패 - 평점 범위 초과")
    void reviewCreateRequestInvalidRating() {
        ReviewCreateRequest request = new ReviewCreateRequest(6, "내용은 충분히 깁니다.");
        Set<ConstraintViolation<ReviewCreateRequest>> violations = validator.validate(request);
        assertThat(violations).hasSize(1);
        assertThat(violations.iterator().next().getMessage()).contains("5");
    }

    @Test
    @DisplayName("ReviewCreateRequest: 실패 - 내용 길이 부족")
    void reviewCreateRequestInvalidContent() {
        ReviewCreateRequest request = new ReviewCreateRequest(5, "짧음");
        Set<ConstraintViolation<ReviewCreateRequest>> violations = validator.validate(request);
        assertThat(violations).isNotEmpty();
        assertThat(violations.iterator().next().getMessage()).contains("10자 이상");
    }

    @Test
    @DisplayName("ReviewUpdateRequest: 유효성 검사 성공")
    void reviewUpdateRequestValid() {
        ReviewUpdateRequest request = new ReviewUpdateRequest("수정된 리뷰 내용입니다. 아주 좋아요.", 4, List.of(1L, 2L));
        Set<ConstraintViolation<ReviewUpdateRequest>> violations = validator.validate(request);
        assertThat(violations).isEmpty();
    }

    @Test
    @DisplayName("PointEarnRequest: 생성자 및 Getter 테스트 (Lombok)")
    void pointEarnRequestTest() {
        PointEarnRequest request = new PointEarnRequest(1L, "EARN_REVIEW", 1000L, 123L);

        assertThat(request.getMemberId()).isEqualTo(1L);
        assertThat(request.getEventType()).isEqualTo("EARN_REVIEW");
        assertThat(request.getPureAmount()).isEqualTo(1000L);
        assertThat(request.getOrderId()).isEqualTo(123L);

        // NoArgs 생성자 테스트
        PointEarnRequest emptyRequest = new PointEarnRequest();
        assertThat(emptyRequest).isNotNull();
    }

    // --- 2. Response DTO Tests ---

    @Test
    @DisplayName("BookReviewResponse: withIsLiked 메서드 동작 확인")
    void bookReviewResponseWithIsLikedTest() {
        // given
        List<ReviewImageResponse> images = List.of(new ReviewImageResponse(1L, "url"));
        BookReviewResponse original = new BookReviewResponse(
                1L, 100L, "testUser", "Content", 5,
                Timestamp.valueOf(LocalDateTime.now()),
                images, 10, false
        );

        // when
        BookReviewResponse likedResponse = original.withIsLiked(true);

        // then
        assertThat(likedResponse.isLiked()).isTrue();
        assertThat(likedResponse.reviewId()).isEqualTo(original.reviewId());
        assertThat(likedResponse.reviewImages()).hasSize(1);
        assertThat(original.isLiked()).isFalse(); // 원본 불변성 확인
    }

    @Test
    @DisplayName("기타 Response Record 생성 및 조회 테스트")
    void otherResponseDtosTest() {
        // MyPageReviewResponse
        MyPageReviewResponse myPage = new MyPageReviewResponse(1L, 2L, "Title", Timestamp.valueOf(LocalDateTime.now()));
        assertThat(myPage.bookTitle()).isEqualTo("Title");

        // ReviewCreateResponse
        ReviewCreateResponse createRes = new ReviewCreateResponse(10L, 5, "Content");
        assertThat(createRes.reviewId()).isEqualTo(10L);

        // UpdateReviewResponse
        UpdateReviewResponse updateRes = new UpdateReviewResponse("New Content", 3);
        assertThat(updateRes.content()).isEqualTo("New Content");
    }

    // --- 3. Event DTO Tests ---

    @Test
    @DisplayName("ReviewCreatedEvent 생성 테스트")
    void reviewCreatedEventTest() {
        ReviewCreatedEvent event = new ReviewCreatedEvent(1L, 100L, "EARN_REVIEW");
        assertThat(event.memberId()).isEqualTo(1L);
        assertThat(event.bookId()).isEqualTo(100L);
    }

    @Test
    @DisplayName("ReviewImageDeleteEvent 생성 테스트")
    void reviewImageDeleteEventTest() {
        List<String> urls = List.of("url1", "url2");
        ReviewImageDeleteEvent event = new ReviewImageDeleteEvent(urls);
        assertThat(event.imageUrls()).hasSize(2);
    }

    @Test
    @DisplayName("ReviewImageUploadEvent 생성 테스트")
    void reviewImageUploadEventTest() {
        MockMultipartFile file = new MockMultipartFile("img", "test.jpg", "image/jpeg", "data".getBytes());
        List<MultipartFile> files = List.of(file);

        ReviewImageUploadEvent event = new ReviewImageUploadEvent(1L, files);

        assertThat(event.reviewId()).isEqualTo(1L);
        assertThat(event.images()).hasSize(1);
    }
}