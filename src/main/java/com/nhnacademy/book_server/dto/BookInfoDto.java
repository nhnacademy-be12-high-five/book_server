package com.nhnacademy.book_server.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BookInfoDto {
    private String isbn;
    private String title;
    private List<String> authors;
    private String publisher;
    private LocalDate publishedDate;
    private Integer price;
    private String image;
    private String description;
    private Integer categoryId;
}
