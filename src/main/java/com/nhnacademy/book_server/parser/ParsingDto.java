package com.nhnacademy.book_server.parser;

import jakarta.persistence.Entity;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.util.List;


@Getter
@Setter
public class ParsingDto {

    // 책에 대한 정보를 저장하는 임시 바구니
    // 계층간 데이터 전달
    private String isbn;
    String title;

    private String author;
    private List<String> authors;
    private String publisher;
    private List<String> publishers;

    private Integer price;
    private String image;
    private String content;
    private String publishedDate;
}
