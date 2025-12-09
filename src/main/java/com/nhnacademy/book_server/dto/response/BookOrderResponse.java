package com.nhnacademy.book_server.dto.response;

import lombok.Builder;
import lombok.Getter;

@Getter
// 주문이 완료된 상태에서 책의 제목 가격을 받음
@Builder
public class BookOrderResponse {
    private Long id;
    private String title;
    private Integer price;
}
