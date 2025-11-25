package com.nhnacademy.book_server.parser;

import com.opencsv.bean.CsvBindByName;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@ToString
public class ParsingDto {

    @CsvBindByName(column = "SEQ_NO")
    private String seqNo;

    @CsvBindByName(column = "ISBN_THIRTEEN_NO")
    private String isbn;

    @CsvBindByName(column = "TITLE_NM")
    private String title;

    @CsvBindByName(column = "AUTHR_NM")
    private String author;

    @CsvBindByName(column = "PUBLISHER_NM")
    private String publisher;

    @CsvBindByName(column = "PBLICTE_DE")
    private String pubDate;

    @CsvBindByName(column = "PRC_VALUE")
    private String price;

    @CsvBindByName(column = "IMAGE_URL")
    private String imageUrl;

    @CsvBindByName(column = "BOOK_INTRCN_CN")
    private String description;
}