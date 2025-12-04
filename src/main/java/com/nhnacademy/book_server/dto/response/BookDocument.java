package com.nhnacademy.book_server.dto.response;

import com.nhnacademy.book_server.dto.BookResponse;

import com.nhnacademy.book_server.entity.BookAuthor;
import lombok.*;

import java.awt.print.Book;
import java.util.List;

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
    private String isbn13;
    private Integer price;
    private String image;
    private Integer categoryId;
    private String content;
    private String publisher;
    private String publishedDate;
    private Double avgRating;
    private Long reviewCount;

    private List<Float> vector;

    // BookResponse -> ES 문서로 변환
    public static BookDocument from(BookResponse response) {

        if(response==null){
            return null;
        }

        return BookDocument.builder()
                .id(response.id())
                .title(response.title())
                .author(response.author())
                .isbn13(response.isbn())
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
