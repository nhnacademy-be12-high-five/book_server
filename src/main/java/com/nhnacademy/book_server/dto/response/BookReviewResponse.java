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
    public BookReviewResponse withPersonalizedData(boolean newIsLiked) {
        Integer adjustedCount = this.likeCount;

        // 내가 좋아요를 눌렀는데(true), 캐시된 개수가 0개라면? -> 최소 1개로 보정!
        if (newIsLiked && this.likeCount == 0) {
            adjustedCount = 1;
        }

        return new BookReviewResponse(
                reviewId, memberId, loginId, content, rating, createdAt, imageUrls,
                adjustedCount, // 보정된 개수
                newIsLiked     // 내 좋아요 상태
        );
    }
}
