package com.nhnacademy.book_server.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.nhnacademy.book_server.entity.Publisher;
import lombok.AllArgsConstructor;
import lombok.RequiredArgsConstructor;

import java.time.LocalDate;

// 책 상세 페이지 response
@RequiredArgsConstructor
@AllArgsConstructor
public class BookDetailResponse {

    private String title;
    private String author;
    private Publisher publisher;
    private LocalDate dateTime;
    private Integer price;  // 원가
    @JsonProperty("imageUrl")
    private String image;
    @JsonProperty("pubDate")
    private String publishDate;
    private String content;
    private boolean stock;

    // 할인가의 1%
    private Integer priceSale;

}
