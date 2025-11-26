package com.nhnacademy.book_server.entity;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum SearchFieldType {
    TITLE(100),
    AUTHOR(90),
    TAG(80),
    ISBN(70),
    PUBLISHER(60), //출판사
    CONTENT(50), //도서 설명
    REVIEW(40);//리뷰 내용

    private final int weight;
}
