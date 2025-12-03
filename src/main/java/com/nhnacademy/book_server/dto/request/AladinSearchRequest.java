package com.nhnacademy.book_server.dto.request;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AladinSearchRequest {

    // 상품 검색 API는 '검색어'를 통한 입력
    // 상품 검색 API Request

    private String ttbKey;
    private String query;
}
