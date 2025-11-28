package com.nhnacademy.book_server.dto.response;

import java.sql.Timestamp;

public record MyPageReviewResponse (Long reviewId,
                                    Long bookId,
                                    String bookTitle,
                                    Timestamp createdAt){
}
