package com.nhnacademy.book_server.parser;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.opencsv.bean.CsvBindByName;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.ToString;


@Data
@ToString
@AllArgsConstructor
@RequiredArgsConstructor
public class ParsingDto {

    @CsvBindByName(column = "SEQ_NO")
    private String seqNo;

    @CsvBindByName(column = "ISBN_THIRTEEN_NO")
    private String isbn;

    @CsvBindByName(column = "TITLE_NM")
    private String title;

    @CsvBindByName(column = "AUTHR_NM")
    @JsonProperty("author")
    private String author;

    @CsvBindByName(column = "PUBLISHER_NM")
    private String publisher;

    @CsvBindByName(column = "TWO_PBLICTE_DE")
    @JsonProperty("pubDate")
    @JsonFormat(pattern = "yyyy-MM-dd")
    private String pubDate;

    @CsvBindByName(column = "PRC_VALUE")
    private String price;

    @CsvBindByName(column = "IMAGE_URL")
    @JsonProperty("imageUrl")
    private String imageUrl;

    @CsvBindByName(column = "BOOK_INTRCN_CN")
    private String description;
    private Integer categoryId;
    private String categoryName;

    public Integer getCategoryId() {
        return categoryId;
    }

    public String getCategoryName() {
        return categoryName;
    }

    public void setCategoryId(Integer categoryId) {
        this.categoryId = categoryId;
    }

    public void setCategoryName(String categoryName) {
        this.categoryName = categoryName;
    }

    private Integer parentId;
    private Integer depth;

}