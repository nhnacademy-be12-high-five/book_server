package com.nhnacademy.book_server.dto.response;

import com.nhnacademy.book_server.dto.BookResponse;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BookDocument {

    //ES에 저장할 문서 dto

    private Long id;
    private String title;
    private String author;
    private String isbn;
    private Integer price;
    private String image;
    private Integer categoryId;
    private String content;
    private String publisher;
    private String publishedDate;
    private Double avgRating;
    private Long reviewCount;

    // BookResponse -> ES 문서로 변환
    public static BookDocument from(BookResponse response) {
        return BookDocument.builder()
                .id(response.id())
                .title(response.title())
                .author(response.author())
                .isbn(response.isbn())
                .price(response.price())
                .image(response.image())
                .categoryId(response.categoryId())
                .content(response.content())
                .publisher(response.publisher())
                .publishedDate(response.publishedDate())
                .avgRating(response.avgRating())
                .reviewCount(response.reviewCount())
                .build();
    }
}
