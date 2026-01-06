package com.nhnacademy.book_server.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.nhnacademy.book_server.dto.response.TagResponse;
import com.nhnacademy.book_server.entity.*;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

@JsonIgnoreProperties(ignoreUnknown = true)
public record BookResponse(
        @JsonProperty("id") Long bookId,
        String title,
        String author,
        String isbn,
        Integer price,
        @JsonProperty("imageUrl")
        String image,
        // 단일 ID에서 리스트 형태로 변경
        List<CategoryResponse> categories,
        List<TagResponse> tags,
        String content,
        String publisher,
        @JsonProperty("pubDate")
        String publishedDate,
        Double avgRating,
        Long reviewCount,
        String aiSummary,
        String aiReviewSummary,
        Integer categoryId,
        Integer parentId
) {

    // =================================================================================
    // [1] 상세 페이지용 (리뷰 요약 포함)
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
        return build(book, book.getBookCategories(), avg, count, null, aiReviewSummary);
    }

    // =================================================================================
    // [2] 일반 목록/검색용
    // =================================================================================
    public static BookResponse from(Book book, List<BookCategory> bookCategories, Double avgRating, Long reviewCount) {
        return build(book, bookCategories, avgRating, reviewCount, null, null);
    }

    // =================================================================================
    // [3] RAG 검색용
    // =================================================================================
    public static BookResponse fromWithAiSummary(Book book, List<BookCategory> bookCategories, Double avgRating, Long reviewCount, String aiSummary) {
        return build(book, bookCategories, avgRating, reviewCount, aiSummary, null);
    }

    // =================================================================================
    // [4] 리뷰 리스트로 평점 계산
    // =================================================================================
    public static BookResponse from(Book book, List<BookCategory> bookCategories, List<Review> reviews) {
        double avg = 0.0;
        long count = 0L;

        if (reviews != null && !reviews.isEmpty()) {
            count = reviews.size();
            avg = reviews.stream()
                    .mapToInt(Review::getRating)
                    .average()
                    .orElse(0.0);
        }
        return build(book, bookCategories, avg, count, null, null);
    }

    // =================================================================================
    // [5] 기본 변환 메서드들
    // =================================================================================
    public static BookResponse from(Book book, List<BookCategory> bookCategories) {
        return build(book, bookCategories,
                book.getAverageRating(),
                book.getReviewCount().longValue(),
                null, null);
    }

    public static BookResponse from(Book book) {
        // Book 엔티티에 매핑된 bookCategories 리스트를 직접 사용
        return build(book, book.getBookCategories(),
                book.getAverageRating(),
                book.getReviewCount().longValue(),
                null, null);
    }

    // ---------------------------------------------------------------------------------
    // [Internal Helper] 생성 로직 통합
    // ---------------------------------------------------------------------------------
    private static BookResponse build(Book book,
                                      List<BookCategory> bookCategories,
                                      Double avgRating,
                                      Long reviewCount,
                                      String aiSummary,
                                      String aiReviewSummary
    ) {
        // 작가 정보 처리
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

        // 출판사 정보 처리
        String publisherName = (book.getPublisher() != null) ? book.getPublisher().getName() : null;

        // 카테고리 리스트 처리 (N:M 대응)
        List<CategoryResponse> categoryList = Collections.emptyList();
        Integer mainCategoryId = null;
        Integer mainParentId = null;

        if (bookCategories != null && !bookCategories.isEmpty()) {
            categoryList = bookCategories.stream()
                    .map(bc -> new CategoryResponse(
                            bc.getCategory().getCategoryId(),
                            bc.getCategory().getCategoryName()))
                    .sorted(Comparator.comparingInt(CategoryResponse::categoryId))
                    .toList();

            Category firstCategory = bookCategories.get(0).getCategory();
            mainCategoryId = firstCategory.getCategoryId();

            if (firstCategory.getParentId() != 0) {
                mainParentId =firstCategory.getParentId();
            }
        }

        List<TagResponse> tagList = Collections.emptyList();
        if (book.getBookTags() != null && !book.getBookTags().isEmpty()){
            tagList=book.getBookTags().stream().map(bookTag -> {
                Tag t = bookTag.getTag();
                return new TagResponse(t.getTagId(),t.getName()
                );
            })
                    .toList();
        }

        return new BookResponse(
                book.getId(),
                book.getTitle(),
                authorNames,
                book.getIsbn13(),
                book.getPrice(),
                book.getImage(),
                categoryList, // List<CategoryResponse> 전달
                tagList,
                book.getContent(),
                publisherName,
                book.getPublishedDate(),
                avgRating,
                reviewCount,
                aiSummary,
                aiReviewSummary,
                mainCategoryId,
                mainParentId
        );
    }
}