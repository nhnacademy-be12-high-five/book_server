package com.nhnacademy.book_server.entity;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

@Getter
@Setter
public class BookUpdateRequest {
    private String title;
    private Integer price;
    @NotBlank(message = "ISBN은 필수 입력 값입니다.")
    private String isbn;

    private String publisher;       // 출판사
    private LocalDate publishedDate; // 출판일
    private String description;      // 책 소개/요약
}
