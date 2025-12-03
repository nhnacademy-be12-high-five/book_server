package com.nhnacademy.book_server.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.util.List;

@Getter
@Setter
public class BookUpdateRequest {
    private String title;
    private Integer price;
    @NotBlank(message = "ISBN은 필수 입력 값입니다.")
    private String isbn;

    private String publisher;       // 출판사
    private String publishedDate; // 출판일
    private String description;      // 책 소개/요약

    private String image;
    private List<String> authors;
}