package com.nhnacademy.book_server.exception;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.*;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class GlobalExceptionHandlerTest {

    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        // GlobalExceptionHandler와 예외를 발생시킬 테스트용 컨트롤러를 연결
        mockMvc = MockMvcBuilders.standaloneSetup(new TestController())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("BusinessException 처리 테스트")
    void handleBusinessException() throws Exception {
        mockMvc.perform(get("/test/business"))
                .andExpect(status().isNotFound()) // Mock ErrorCode가 NOT_FOUND 반환하도록 설정됨
                .andExpect(jsonPath("$.code").value("ERR001"))
                .andExpect(jsonPath("$.message").value("Business Error Occurred"));
    }

    @Test
    @DisplayName("MethodArgumentNotValidException 처리 테스트 (Validation 실패)")
    void handleValidationException() throws Exception {
        // 빈 객체를 보내서 @NotNull 위반 유발
        TestDto emptyDto = new TestDto();

        mockMvc.perform(post("/test/validation")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(emptyDto)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("C001"))
                // 에러 메시지는 Validation 어노테이션의 defaultMessage가 나옴
                .andExpect(jsonPath("$.message").exists()); 
    }

    @Test
    @DisplayName("DataIntegrityViolationException 처리 테스트 (DB 에러)")
    void handleDataBaseException() throws Exception {
        mockMvc.perform(get("/test/db-error"))
                // 코드에서 HttpStatus.ALREADY_REPORTED (208)을 반환하고 있음
                .andExpect(status().isAlreadyReported()) 
                .andExpect(jsonPath("$.code").value("DB001"))
                .andExpect(jsonPath("$.message").value("이미 리뷰를 작성하셨습니다."));
    }

    @Test
    @DisplayName("IllegalArgumentException 처리 테스트")
    void handleIllegalArgumentException() throws Exception {
        mockMvc.perform(get("/test/illegal-arg"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("C003"))
                .andExpect(jsonPath("$.message").value("Invalid Argument"));
    }

    @Test
    @DisplayName("Exception (알 수 없는 에러) 처리 테스트")
    void handleException() throws Exception {
        mockMvc.perform(get("/test/general"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("C002"))
                .andExpect(jsonPath("$.message").value("알 수 없는 서버 오류가 발생했습니다."));
    }

    // --- 테스트를 위한 더미 컨트롤러 및 DTO ---

    @RestController
    @RequestMapping("/test")
    static class TestController {

        @GetMapping("/business")
        void throwBusinessException() {
            // BusinessException과 ErrorCode를 Mocking하여 실제 Enum 의존성 없이 테스트
            ErrorCode mockErrorCode = mock(ErrorCode.class);
            when(mockErrorCode.getStatus()).thenReturn(HttpStatus.NOT_FOUND);
            when(mockErrorCode.getCode()).thenReturn("ERR001");
            when(mockErrorCode.getMessage()).thenReturn("Business Error Occurred");

            // BusinessException 생성자가 ErrorCode를 받는다고 가정
            throw new BusinessException(mockErrorCode);
        }

        @PostMapping("/validation")
        void throwValidationException(@RequestBody @Valid TestDto dto) {
            // Validation 실패 시 자동으로 MethodArgumentNotValidException 발생
        }

        @GetMapping("/db-error")
        void throwDbException() {
            throw new DataIntegrityViolationException("DB Constraint Violation");
        }

        @GetMapping("/illegal-arg")
        void throwIllegalArgument() {
            throw new IllegalArgumentException("Invalid Argument");
        }

        @GetMapping("/runtime")
        void throwRuntime() {
            throw new RuntimeException("Runtime Error");
        }

        @GetMapping("/general")
        void throwGeneral() throws Exception {
            throw new Exception("General Error");
        }
    }

    static class TestDto {
        @NotNull(message = "Value cannot be null")
        private String value;

        public String getValue() { return value; }
        public void setValue(String value) { this.value = value; }
    }
}