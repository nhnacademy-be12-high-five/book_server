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
    REVIEW_WRITE_AUTHOR(HttpStatus.NON_AUTHORITATIVE_INFORMATION, "R003", "해당 책을 구매한 맴버가 아닙니다."),
    REVIEW_DUP(HttpStatus.NOT_ACCEPTABLE, "R004", "이미 해당 도서에 대한 리뷰를 작성하셨습니다"),
    REVIEW_IMAGE_LIMIT_EXCEEDED(HttpStatus.NOT_ACCEPTABLE,"R005" , "등록 가능한 이미지 수를 넘었습니다."),
    REVIEW_NOT_MATCH_BOOK(HttpStatus.NOT_FOUND, "R006" , "해당 리뷰는 이 책에 작성된 리뷰가 아닙니다." ),

    // Auth
    CART_ACCESS_DENIED(HttpStatus.FORBIDDEN, "A001", "해당 장바구니에 대한 접근 권한이 없습니다."),

    // External (Book Service)
    BOOK_NOT_FOUND(HttpStatus.NOT_FOUND, "B001", "존재하지 않는 책입니다."),
    BOOK_SERVICE_ERROR(HttpStatus.SERVICE_UNAVAILABLE, "B002", "도서 서비스 응답이 지연되고 있습니다."),

    // External (외부 서비스 관련)
    EXTERNAL_SERVER_ERROR(HttpStatus.SERVICE_UNAVAILABLE, "EXT001", "외부 서비스 통신 중 오류가 발생했습니다."),
    BOOK_NOT_FOUND_IN_SERVER(HttpStatus.NOT_FOUND, "EXT002", "도서 서비스에서 해당 책을 찾을 수 없습니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;
}
