package com.nhnacademy.book_server.dto.response;

import com.nhnacademy.book_server.dto.BookResponse;
import lombok.*;
import org.springframework.data.elasticsearch.annotations.Document;

import java.util.List;

@Getter
@Setter
@Document(indexName = "high-five")
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
                .id(response.bookId())
                .title(response.title())
                .author(response.author())
                .isbn13(response.isbn())
                .price(response.price())
                .image(response.image())
                .content(response.content())
                .publisher(response.publisher())
                .publishedDate(response.publishedDate())
                .avgRating(response.avgRating())
                .reviewCount(response.reviewCount())
                .build();
    }


}
