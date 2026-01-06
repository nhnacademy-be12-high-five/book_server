package com.nhnacademy.book_server.dto.request;

import lombok.Getter;
import lombok.Setter;

public class AladinListRequest {
    private AladinListRequest() {
        throw new IllegalStateException("Utility class");
    }

    // 상품 리스트 API 는 제공하는 리스트 중 선택

    @Getter
    @Setter
    public static class Items{
       public String title;
       public String image;
       public String author;
       public String isbn;
       public String description;
    }
}
