package com.nhnacademy.book_server.dto.response;

import java.sql.Timestamp;
import java.util.List;

// memberId -> loginId로 바꿔야함
public record BookReviewResponse(Long reviewId,
                                 Long memberId,
                                 String content,
                                 int rating,
                                 Timestamp createdAt,
                                 List<String> imageUrls){}
