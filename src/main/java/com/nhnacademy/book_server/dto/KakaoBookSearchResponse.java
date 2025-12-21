package com.nhnacademy.book_server.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@JsonIgnoreProperties(ignoreUnknown = true)
public class KakaoBookSearchResponse {

    private List<Document> documents;
    private Meta meta;

    @Getter
    @Setter
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Document {
        private String title;
        private String contents;
        private String url;
        private String isbn;

        @JsonProperty("datetime")
        private String datetime;

        private List<String> authors;
        private String publisher;
        private List<String> translators;
        private Integer price;

        @JsonProperty("sale_price")
        private Integer salePrice;

        private String thumbnail;
        private String status;
    }

    @Getter
    @Setter
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Meta {
        @JsonProperty("total_count")
        private Integer totalCount;   // 검색된 문서 수

        @JsonProperty("pageable_count")
        private Integer pageableCount; // 노출 가능 문서 수

        @JsonProperty("is_end")
        private Boolean isEnd;
    }
}
