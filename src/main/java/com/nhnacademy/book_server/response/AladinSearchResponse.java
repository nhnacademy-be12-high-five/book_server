package com.nhnacademy.book_server.response;

import com.nhnacademy.book_server.entity.AladinItem;
import lombok.Getter;
import lombok.Setter;

import java.util.Date;
import java.util.List;

@Getter
@Setter
public class AladinSearchResponse {

    // 상품 검색/상품 리스트/상품 조회 API Response

    private String tile;  // API 결과의 제목
    private Integer totalResults;  // API의 총 결과 수
    private Integer startIndex; // Page 수
    private Integer itemsPerPage;  // 한페이지에 출력될 상품 수

    private Integer searchCategoryId; // 분야로 조회한 경우 해당 분야의 ID
    private String searchCategoryName; //분야로 조회한 경우 해당 분야의 분야명

    private String title;
    private String link; // 상품 링크 URL

    private String author;
    private String pubdate; // 출시일
    private String description;  // 상품 설명

    private String isbn;
    private String isbn13;

    private Integer pricesales; // 판매가
    private Integer pricestandard;  // 정가

    private List<AladinItem> item;
}
