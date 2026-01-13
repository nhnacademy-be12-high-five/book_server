package com.nhnacademy.book_server.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class BookCreateRequest {
    @NotBlank(message = "ISBN은 필수 입력 값입니다.")
    private String isbn;

    @NotBlank(message = "도서 제목은 필수 입력 값입니다.")
    private String title;

    @NotNull(message = "가격은 필수 입력 값입니다.")
    @Min(value = 0, message = "가격은 0원 이상이어야 합니다.")
    private Integer price;

    private String publisher;
    private String publishedDate;
    private String description;
    @JsonProperty("imageUrl")
    private String image;

    private Integer categoryId;

    private String author;
}
