package com.nhnacademy.book_server.entity;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ReviewTest {

    @Test
    @DisplayName("리뷰 생성 확인")
    void createReview() {
        // Given
        Book book = new Book();
        Long memberId = 1L;

        // When
        Review review = new Review(5, "내용", book, memberId);

        // Then
        assertThat(review.getRating()).isEqualTo(5);
        assertThat(review.getReviewContent()).isEqualTo("내용");
        assertThat(review.getBook()).isEqualTo(book);
        assertThat(review.getMemberId()).isEqualTo(memberId);
        assertThat(review.getLikeCount()).isEqualTo(0);
    }

    @Test
    @DisplayName("리뷰 업데이트 확인")
    void updateReview() {
        // Given
        Review review = new Review(5, "내용", new Book(), 1L);

        // When
        review.update(3, "수정된 내용");

        // Then
        assertThat(review.getRating()).isEqualTo(3);
        assertThat(review.getReviewContent()).isEqualTo("수정된 내용");
    }
}