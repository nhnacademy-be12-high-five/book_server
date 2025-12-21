//package com.nhnacademy.book_server.dto;
//
//import com.nhnacademy.book_server.dto.response.BookReviewResponse;
//import com.nhnacademy.book_server.entity.Review;
//import com.nhnacademy.book_server.entity.ReviewImage;
//import com.nhnacademy.book_server.entity.ReviewLike;
//import com.nhnacademy.book_server.entity.BookReviewAi;
//import com.nhnacademy.book_server.entity.Book;
//import org.junit.jupiter.api.DisplayName;
//import org.junit.jupiter.api.Test;
//
//import java.sql.Timestamp;
//import java.time.Instant;
//import java.util.List;
//
//import static org.assertj.core.api.Assertions.assertThat;
//
//class ReviewEntityDtoTest {
//
//    @Test
//    @DisplayName("Review Entity - 수정 및 좋아요 카운트 테스트")
//    void reviewEntityTest() {
//        Review review = new Review(5, "content", new Book(), 1L, false);
//
//        // Update
//        review.update(4, "new content");
//        assertThat(review.getRating()).isEqualTo(4);
//        assertThat(review.getReviewContent()).isEqualTo("new content");
//
//        // Like Count
//        review.increaseLikeCount();
//        assertThat(review.getLikeCount()).isEqualTo(1);
//
//        review.decreaseLikeCount();
//        assertThat(review.getLikeCount()).isEqualTo(0);
//
//        review.decreaseLikeCount(); // 0 이하 방지 확인
//        assertThat(review.getLikeCount()).isEqualTo(0);
//    }
//
//    @Test
//    @DisplayName("ReviewImage Entity 생성")
//    void reviewImageTest() {
//        Review review = new Review();
//        ReviewImage image = new ReviewImage(review, "http://url.com");
//
//        assertThat(image.getReview()).isEqualTo(review);
//        assertThat(image.getFileUrl()).isEqualTo("http://url.com");
//    }
//
//    @Test
//    @DisplayName("ReviewLike Entity 생성")
//    void reviewLikeTest() {
//        Review review = new Review();
//        ReviewLike like = new ReviewLike(review, 100L);
//
//        assertThat(like.getReview()).isEqualTo(review);
//        assertThat(like.getMemberId()).isEqualTo(100L);
//    }
//
//    @Test
//    @DisplayName("BookReviewAi Entity 업데이트")
//    void bookReviewAiTest() {
//        BookReviewAi ai = new BookReviewAi(new Book(), "summary", 10L, 4.5);
//
//        ai.updateSummary("new summary", 11L, 4.6);
//
//        assertThat(ai.getSummary()).isEqualTo("new summary");
//        assertThat(ai.getLastReviewCount()).isEqualTo(11L);
//    }
//
//    @Test
//    @DisplayName("BookReviewResponse - withIsLiked 테스트")
//    void bookReviewResponseTest() {
//        BookReviewResponse response = new BookReviewResponse(
//                1L, 100L, "id", "content", 5, Timestamp.from(Instant.now()),
//                List.of(), 0, false, false
//        );
//
//        BookReviewResponse likedResponse = response.withIsLiked(true);
//
//        assertThat(likedResponse.isLiked()).isTrue();
//        assertThat(likedResponse.reviewId()).isEqualTo(response.reviewId());
//    }
//}