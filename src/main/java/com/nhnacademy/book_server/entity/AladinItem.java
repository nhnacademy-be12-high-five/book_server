package com.nhnacademy.book_server.entity;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AladinItem {

    private String title;
    private String link;
    private String author;

    @JsonProperty("pubDate") // JSON 키가 'pubDate'라고 가정
    private String pubDate;

    private String description;
    private String isbn13;

    @JsonProperty("priceSales")
    private Integer priceSales;

    @JsonProperty("priceStandard")
    private Integer priceStandard;

}