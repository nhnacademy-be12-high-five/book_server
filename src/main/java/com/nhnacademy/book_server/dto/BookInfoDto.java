package com.nhnacademy.book_server.dto;

import com.nhnacademy.book_server.entity.BookAuthor;
import lombok.*;

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
