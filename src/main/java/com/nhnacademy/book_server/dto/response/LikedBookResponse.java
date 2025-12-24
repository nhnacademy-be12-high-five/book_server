package com.nhnacademy.book_server.dto.response;

import com.nhnacademy.book_server.entity.Book;
import com.nhnacademy.book_server.entity.BookAuthor;
import lombok.Builder;
import lombok.Getter;

import java.util.stream.Collectors;

@Getter
@Builder
public class LikedBookResponse {

    private Long bookId;           // 책 ID
    private String title;           // 제목
    private String author;          // 저자 문자열 (여러 명이면 ", "로 연결)
    private String publisher;       // 출판사명
    private String thumbnailUrl;    // 표지 이미지
    private Integer price;          // 가격

    // 프론트 UI용 (현재 정책 없으므로 기본 false)
    private boolean isFreeShipping;
    private boolean isTodayShipping;

    /**
     * Book → 찜목록 DTO 변환
     */
    public static LikedBookResponse from(Book book) {

        /* 1. 저자명 추출
           BookAuthor -> Author -> name */
        String authorNames =
                (book.getBookAuthors() == null || book.getBookAuthors().isEmpty())
                        ? ""
                        : book.getBookAuthors().stream()
                        .map(BookAuthor::getAuthor)          // Author 엔티티
                        .map(author -> author.getName())     // ★ Author.name
                        .collect(Collectors.joining(", "));

        /* 2. 출판사명 (null-safe) */
        String publisherName =
                (book.getPublisher() != null)
                        ? book.getPublisher().getName()
                        : "";

        return LikedBookResponse.builder()
                .bookId(book.getId())
                .title(book.getTitle())
                .author(authorNames)
                .publisher(publisherName)
                .thumbnailUrl(book.getImage())   // image_url
                .price(book.getPrice())
                .isFreeShipping(false)
                .isTodayShipping(false)
                .build();
    }
}
