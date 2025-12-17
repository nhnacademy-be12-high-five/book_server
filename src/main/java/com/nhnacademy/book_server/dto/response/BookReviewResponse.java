package com.nhnacademy.book_server.dto.response;
import java.sql.Timestamp;
import java.util.List;

import java.io.Serializable;

public record BookReviewResponse(
        Long reviewId,
        Long memberId,
        String loginId,
        String content,
        int rating,
        Timestamp createdAt,
        List<String> imageUrls,
        Integer likeCount,
        Boolean isLiked
) implements Serializable {
    public BookReviewResponse withIsLiked(boolean newIsLiked) {
        return new BookReviewResponse(
                this.reviewId(), this.memberId, this.loginId(), this.content(), this.rating(),
                this.createdAt(), this.imageUrls(), this.likeCount(),
                newIsLiked // 여기만 교체!
        );
    }
}
