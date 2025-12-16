package com.nhnacademy.book_server.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.nhnacademy.book_server.entity.*;

import java.util.List;
import java.util.stream.Collectors;

public record BookResponse(
        @JsonProperty("id") Long bookId,
        String title,
        String author,
        String isbn,
        Integer price,
        String image,
        Integer categoryId,
        String content,
        String publisher,
        String publishedDate,
        Double avgRating,
        Long reviewCount,
        String aiSummary,       // (1) RAG 검색용 요약
        String aiReviewSummary  // (2) 리뷰 요약 (상세페이지용)
) {

    // =================================================================================
    // [1] 상세 페이지용 (리뷰 요약 포함) - 이름 변경으로 모호성 제거
    // =================================================================================
    public static BookResponse fromWithReviewSummary(Book book, String aiReviewSummary, List<Review> reviews) {
        double avg = 0.0;
        long count = 0L;

        if (reviews != null && !reviews.isEmpty()) {
            count = reviews.size();
            avg = reviews.stream()
                    .mapToInt(Review::getRating)
                    .average()
                    .orElse(0.0);
        }
        // Category는 null로 전달
        return build(book, book.getCategory(), avg, count, null, aiReviewSummary);
    }

    // =================================================================================
    // [2] 일반 목록/검색용 (기존 유지)
    // =================================================================================
    public static BookResponse from(Book book, Category category, Double avgRating, Long reviewCount) {
        return build(book, category, avgRating, reviewCount, null, null);
    }

    // =================================================================================
    // [3] RAG 검색용 (기존 유지)
    // =================================================================================
    public static BookResponse fromWithAiSummary(Book book, Category category, Double avgRating, Long reviewCount, String aiSummary) {
        return build(book, category, avgRating, reviewCount, aiSummary, null);
    }

    // =================================================================================
    // [4] 리뷰 리스트로 평점 계산 (무한루프 수정됨)
    // =================================================================================
    public static BookResponse from(Book book, Category category, List<Review> reviews) {
        double avg = 0.0;
        long count = 0L;

        if (reviews != null && !reviews.isEmpty()) {
            count = reviews.size();
            avg = reviews.stream()
                    .mapToInt(Review::getRating)
                    .average()
                    .orElse(0.0);
        }
        // 여기서 build를 직접 호출하여 재귀 방지
        return build(book, category, avg, count, null, null);
    }

    public static BookResponse from(Book book, Category category) {
        return build(book, category, 0.0, 0L, null, null);
    }

    public static BookResponse from(Book book) {
        return from(book, book.getCategory());
    }


    // ---------------------------------------------------------------------------------
    // [Internal Helper] 생성 로직 통합 (중복 제거)
    // ---------------------------------------------------------------------------------
    private static BookResponse build(Book book,
                                      Category category,
                                      Double avgRating,
                                      Long reviewCount,
                                      String aiSummary,
                                      String aiReviewSummary
    ) {
        String authorNames = null;
        if (book.getBookAuthors() != null && !book.getBookAuthors().isEmpty()) {
            authorNames = book.getBookAuthors().stream()
                    .map(BookAuthor::getAuthor)
                    .filter(author -> author != null && author.getName() != null)
                    .map(a -> a.getName().trim())
                    .filter(name -> !name.isBlank())
                    .distinct()
                    .collect(Collectors.joining(", "));
        }

        String publisherName = null;
        if (book.getPublisher() != null) {
            publisherName = book.getPublisher().getName();
        }

        Integer categoryIdValue = (category != null) ? category.getCategoryId() : null;

        return new BookResponse(
                book.getId(),
                book.getTitle(),
                authorNames,
                book.getIsbn13(),
                book.getPrice(),
                book.getImage(),
                categoryIdValue,
                book.getContent(),
                publisherName,
                book.getPublishedDate(),
                avgRating,
                reviewCount,
                aiSummary,
                aiReviewSummary
        );
    }
}