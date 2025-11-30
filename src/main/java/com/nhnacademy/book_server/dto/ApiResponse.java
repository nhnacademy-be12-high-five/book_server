package com.nhnacademy.book_server.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
@AllArgsConstructor
public class ApiResponse<T> {

    private int code;
    private HttpStatus status;
    private String message;

    private T data;

    // 성공했을 때 (데이터 있음)
    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(200, HttpStatus.OK, "요청이 성공했습니다.", data);
    }

    // 생성 성공했을 때 (데이터 있음 - ID 등)
    public static <T> ApiResponse<T> createSuccess(T data) {
        return new ApiResponse<>(201, HttpStatus.CREATED, "성공적으로 등록되었습니다.", data);
    }

    // 성공했는데 줄 데이터는 없을 때 (삭제 등)
    public static <T> ApiResponse<T> successNoContent() {
        return new ApiResponse<>(200, HttpStatus.OK, "성공했습니다.", null);
    }

    // 실패했을 때 (나중에 예외처리에서 사용)
    public static <T> ApiResponse<T> error(int code, String message) {
        return new ApiResponse<>(code, HttpStatus.BAD_REQUEST, message, null);
    }
}