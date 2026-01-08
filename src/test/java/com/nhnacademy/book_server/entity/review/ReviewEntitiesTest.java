package com.nhnacademy.book_server.entity.review;

import com.nhnacademy.book_server.entity.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ReviewEntitiesTest {

    @Nested
    @DisplayName("Review Entity 테스트")
    class ReviewTest {

        @Test
        @DisplayName("생성자 및 기본값 확인")
        void createReview() {
            Book book = Book.builder().title("Test Book").build();
            Review review = new Review(5, "Great Book", book, 1L);

            assertThat(review.getRating()).isEqualTo(5);
            assertThat(review.getReviewContent()).isEqualTo("Great Book");
            assertThat(review.getBook()).isEqualTo(book);
            assertThat(review.getMemberId()).isEqualTo(1L);
            assertThat(review.getLikeCount()).isZero(); // 기본값 0
            assertThat(review.getReviewImages()).isEmpty();
        }

        @Test
        @DisplayName("리뷰 내용 수정")
        void updateReview() {
            Review review = new Review(5, "Old Content", null, 1L);
            review.update(3, "New Content");

            assertThat(review.getRating()).isEqualTo(3);
            assertThat(review.getReviewContent()).isEqualTo("New Content");
        }

        @Test
        @DisplayName("좋아요 증가")
        void increaseLikeCount() {
            Review review = new Review();
            // likeCount가 null일 경우 0으로 초기화 후 증가되는지 확인
            review.increaseLikeCount();
            assertThat(review.getLikeCount()).isEqualTo(1);

            review.increaseLikeCount();
            assertThat(review.getLikeCount()).isEqualTo(2);
        }

        @Test
        @DisplayName("좋아요 감소 (0 이하로 내려가지 않음)")
        void decreaseLikeCount() {
            Review review = new Review();
            // 0 -> 0
            review.decreaseLikeCount();
            assertThat(review.getLikeCount()).isZero();

            // 1 -> 0
            review.increaseLikeCount();
            review.decreaseLikeCount();
            assertThat(review.getLikeCount()).isZero();
        }
    }

    @Nested
    @DisplayName("ReviewLike Entity 테스트")
    class ReviewLikeTest {
        @Test
        @DisplayName("ReviewLike 생성 확인")
        void createReviewLike() {
            Review review = new Review();
            ReviewLike like = new ReviewLike(review, 100L);

            assertThat(like.getReview()).isEqualTo(review);
            assertThat(like.getMemberId()).isEqualTo(100L);
        }
    }

    @Nested
    @DisplayName("ReviewImage Entity 테스트")
    class ReviewImageTest {
        @Test
        @DisplayName("ReviewImage 생성 확인")
        void createReviewImage() {
            Review review = new Review();
            String url = "https://example.com/image.jpg";
            ReviewImage image = new ReviewImage(review, url);

            assertThat(image.getReview()).isEqualTo(review);
            assertThat(image.getFileUrl()).isEqualTo(url);
        }
    }

    @Nested
    @DisplayName("BookReviewAi Entity 테스트")
    class BookReviewAiTest {
        @Test
        @DisplayName("BookReviewAi 생성 및 업데이트 확인")
        void bookReviewAiLogic() {
            // given
            Book book = Book.builder().id(1L).build();
            BookReviewAi ai = new BookReviewAi(book, "Initial Summary", 10L, 4.0);

            // when: 생성 확인
            assertThat(ai.getBook()).isEqualTo(book);
            assertThat(ai.getSummary()).isEqualTo("Initial Summary");
            assertThat(ai.getLastReviewCount()).isEqualTo(10L);

            // when: 업데이트
            ai.updateSummary("Updated Summary", 15L, 4.2);

            // then: 변경 확인
            assertThat(ai.getSummary()).isEqualTo("Updated Summary");
            assertThat(ai.getLastReviewCount()).isEqualTo(15L);
            assertThat(ai.getLastAvgRating()).isEqualTo(4.2);
        }
    }
}