package com.nhnacademy.book_server.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ErrorCode {

    // C는 공통 오류, CT는 카트 오류, A는 권한 오류, B는 책 오류, EXT는 책 서버 오류

    // Common
    INVALID_INPUT_VALUE(HttpStatus.BAD_REQUEST, "C001", "잘못된 입력값입니다."),
    INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "C002", "서버 내부 오류가 발생했습니다."),

    // Review
    REVIEW_NOT_FOUND(HttpStatus.NOT_FOUND, "R001", "존재하지 않는 리뷰입니다."),
    REVIEW_NOT_AUTHOR(HttpStatus.NON_AUTHORITATIVE_INFORMATION, "R002", "작성자의 리뷰가 아닙니다."),

    // Cart
    CART_NOT_FOUND(HttpStatus.NOT_FOUND, "CT001", "해당 장바구니를 찾을 수 없습니다."),
    CART_ITEM_NOT_FOUND(HttpStatus.NOT_FOUND, "CT002", "장바구니에 해당 상품이 존재하지 않습니다."),
    INVALID_QUANTITY(HttpStatus.BAD_REQUEST, "CT003", "수량은 1개 이상이어야 합니다."),

    // Auth
    CART_ACCESS_DENIED(HttpStatus.FORBIDDEN, "A001", "해당 장바구니에 대한 접근 권한이 없습니다."),

    // External (Book Service)
    BOOK_NOT_FOUND(HttpStatus.NOT_FOUND, "B001", "존재하지 않는 책입니다."),
    BOOK_SERVICE_ERROR(HttpStatus.SERVICE_UNAVAILABLE, "B002", "도서 서비스 응답이 지연되고 있습니다."),

    // External (외부 서비스 관련)
    EXTERNAL_SERVER_ERROR(HttpStatus.SERVICE_UNAVAILABLE, "EXT001", "외부 서비스 통신 중 오류가 발생했습니다."),
    BOOK_NOT_FOUND_IN_SERVER(HttpStatus.NOT_FOUND, "EXT002", "도서 서비스에서 해당 책을 찾을 수 없습니다."),

    // Point (포인트 관련)
    POINT_NOT_FOUND(HttpStatus.NOT_FOUND, "P001", "포인트 정보를 찾을 수 없습니다."),
    POINT_NOT_ENOUGH(HttpStatus.BAD_REQUEST, "P002", "포인트 잔액이 부족합니다."),
    MEMBER_NOT_FOUND(HttpStatus.NOT_FOUND, "P003", "존재하지 않는 회원입니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;
}
