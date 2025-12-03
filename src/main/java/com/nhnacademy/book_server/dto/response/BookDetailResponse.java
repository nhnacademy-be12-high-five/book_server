package com.nhnacademy.book_server.dto.response;

import com.nhnacademy.book_server.entity.Publisher;

import java.time.LocalDate;

// 책 상세 페이지 response
public class BookDetailResponse {

    private String title;
    private String author;
    private Publisher publisher;
    private LocalDate dateTime;
    private Integer price;  // 원가
    private String image;
    private String publishedDate;
    private String isbn13;
    private String content;
    private boolean stock;

//포장 여부
    private boolean WrappedOr;

    // 할인가의 1%
    private Integer priceSale;

    // 배송정보는 고정 
}
