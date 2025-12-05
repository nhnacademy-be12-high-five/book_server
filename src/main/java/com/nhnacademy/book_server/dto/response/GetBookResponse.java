package com.nhnacademy.book_server.dto.response;

public record GetBookResponse(Long bookId,
                                 String title,
                                 Integer price,
                                 String image) {}
// 제목 저자 가격 사진 수량 총가격
