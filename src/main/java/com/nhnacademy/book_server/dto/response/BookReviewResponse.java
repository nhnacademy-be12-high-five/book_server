package com.nhnacademy.book_server.dto.response;

import java.io.Serializable;
import java.sql.Timestamp;
import java.util.List;

public record BookReviewResponse(
        Long reviewId,
        Long memberId,
        String loginId,
        String content,
        int rating,
        Timestamp createdAt,
        List<ReviewImageResponse> reviewImages,
        Integer likeCount,
        Boolean isLiked
) implements Serializable {

    public BookReviewResponse withIsLiked(boolean newIsLiked) {
        return new BookReviewResponse(
                this.reviewId(), this.memberId(), this.loginId(), this.content(), this.rating(),
                this.createdAt(), this.reviewImages(), this.likeCount(), // reviewImages()로 변경
                newIsLiked
        );
    }
}