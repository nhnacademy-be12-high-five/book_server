package com.nhnacademy.book_server.dto;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import static org.assertj.core.api.Assertions.assertThat;

class ApiResponseTest {

    @Test
    @DisplayName("success(data): 요청 성공 (200 OK) 및 데이터 반환 확인")
    void success_Test() {
        // Given
        String testData = "test data";

        // When
        ApiResponse<String> response = ApiResponse.success(testData);

        // Then
        assertThat(response.getCode()).isEqualTo(200);
        assertThat(response.getStatus()).isEqualTo(HttpStatus.OK);
        // 코드에 정의된 메시지 확인
        assertThat(response.getMessage()).isEqualTo("요청이 성공했습니다.");
        assertThat(response.getData()).isEqualTo(testData);
    }

    @Test
    @DisplayName("createSuccess(data): 생성 성공 (201 CREATED) 및 데이터 반환 확인")
    void createSuccess_Test() {
        // Given
        Long createdId = 100L;

        // When
        ApiResponse<Long> response = ApiResponse.createSuccess(createdId);

        // Then
        assertThat(response.getCode()).isEqualTo(201);
        assertThat(response.getStatus()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getMessage()).isEqualTo("성공적으로 등록되었습니다.");
        assertThat(response.getData()).isEqualTo(createdId);
    }

    @Test
    @DisplayName("successNoContent(): 데이터 없는 성공 (200 OK) 확인")
    void successNoContent_Test() {
        // When
        ApiResponse<Void> response = ApiResponse.successNoContent();

        // Then
        assertThat(response.getCode()).isEqualTo(200);
        assertThat(response.getStatus()).isEqualTo(HttpStatus.OK);
        assertThat(response.getMessage()).isEqualTo("성공했습니다.");
        assertThat(response.getData()).isNull();
    }

    @Test
    @DisplayName("error(code, message): 에러 응답 생성 확인 (Status는 BAD_REQUEST 고정)")
    void error_Test() {
        // Given
        int customErrorCode = 404;
        String errorMessage = "Not Found Error";

        // When
        ApiResponse<Object> response = ApiResponse.error(customErrorCode, errorMessage);

        // Then
        assertThat(response.getCode()).isEqualTo(customErrorCode);
        // error 메서드 내부에서 HttpStatus.BAD_REQUEST로 고정되어 있음
        assertThat(response.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getMessage()).isEqualTo(errorMessage);
        assertThat(response.getData()).isNull();
    }

    @Test
    @DisplayName("생성자 및 Getter 테스트 (Lombok)")
    void constructorAndGetter_Test() {
        // Given
        int code = 500;
        HttpStatus status = HttpStatus.INTERNAL_SERVER_ERROR;
        String message = "Server Error";
        String data = "Error Detail";

        // When
        ApiResponse<String> response = new ApiResponse<>(code, status, message, data);

        // Then
        assertThat(response.getCode()).isEqualTo(code);
        assertThat(response.getStatus()).isEqualTo(status);
        assertThat(response.getMessage()).isEqualTo(message);
        assertThat(response.getData()).isEqualTo(data);
    }
}