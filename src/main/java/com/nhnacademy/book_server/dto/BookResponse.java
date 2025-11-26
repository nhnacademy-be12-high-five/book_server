package com.nhnacademy.book_server.dto;

import com.nhnacademy.book_server.entity.Book;
import com.nhnacademy.book_server.entity.Category;
import com.nhnacademy.book_server.entity.Review;

import java.util.Date;
import java.util.List;

public record BookResponse(Long id,
                           String title,
                           String author,
                           String isbn,
                           Long price,
                           String image,
                           Integer categoryId,
                           String content,
                           String publisher,
                           Date publishedDate,
                           Double avgRating,
                           Long reviewCount
) {

    // 1) 기본 팩토리: 평균평점·리뷰수까지 계산된 값이 넘어오는 경우
    public static BookResponse from(Book book,
                                    Category category,
                                    Double avgRating,
                                    Long reviewCount) {
        return new BookResponse(
                book.getBookId(),
                book.getTitle(),
                book.getIsbn(),
                book.getAuthor(),
                book.getPrice(),
                book.getImage(),
                category != null ? category.getCategoryId() : null,
                book.getContent(),
                book.getPublisher(),
                book.getPublishedDate(),
                avgRating,
                reviewCount
        );
    }

    // 2) 리뷰 리스트를 그대로 받아서 평균·개수를 계산하는 팩토리
    public static BookResponse from(Book book,
                                    Category category,
                                    List<Review> reviews) {
        double avg = 0.0;
        long count = 0L;

        if (reviews != null && !reviews.isEmpty()) {
            count = reviews.size();
            avg = reviews.stream()
                    .mapToInt(Review::getRating)
                    .average()
                    .orElse(0.0);
        }

        return from(book, category, avg, count);
    }

    // 3) 카테고리 조회 등에서 리뷰 정보 없이 쓰는 기본 팩토리
    public static BookResponse from(Book book, Category category) {
        return from(book, category, null, 0L);
    }
}
