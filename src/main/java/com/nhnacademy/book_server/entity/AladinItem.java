package com.nhnacademy.book_server.entity;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;

@Getter
@Setter
@RequiredArgsConstructor
@AllArgsConstructor
public class AladinItem {

    private String title;
    private String link;
    private String author;

    @JsonProperty("pubDate") // JSON 키가 'pubDate'라고 가정
    private String pubDate;

    private String isbn13;
    private String description;

    @JsonProperty("priceSales")
    private Integer priceSales; // 판매가

    @JsonProperty("priceStandard")
    private Integer priceStandard; // 원가

}