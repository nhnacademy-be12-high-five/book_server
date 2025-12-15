package com.nhnacademy.book_server.dto.response;
import java.sql.Timestamp;
import java.util.List;

import java.io.Serializable;

public record BookReviewResponse(
        Long reviewId,
        String loginId,
        String content,
        int rating,
        Timestamp createdAt,
        List<String> imageUrls
) implements Serializable {
}
