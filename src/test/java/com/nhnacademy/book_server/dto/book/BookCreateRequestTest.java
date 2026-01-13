package com.nhnacademy.book_server.dto.book; // 또는 dto.request 패키지

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nhnacademy.book_server.dto.request.BookCreateRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class BookCreateRequestTest {

    private Validator validator;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
        objectMapper = new ObjectMapper();
    }

    @Test
    @DisplayName("도서 생성 요청 - 모든 필드가 유효할 때 성공")
    void validBookCreateRequest() {
        BookCreateRequest request = new BookCreateRequest();
        request.setIsbn("1234567890");
        request.setTitle("Effective Java");
        request.setPrice(30000);
        request.setPublisher("Insight");
        request.setPublishedDate("2024-01-01");
        request.setImage("image_url");

        Set<ConstraintViolation<BookCreateRequest>> violations = validator.validate(request);
        assertThat(violations).isEmpty();
    }

    @Test
    @DisplayName("도서 생성 요청 - 필수값(ISBN, 제목, 가격) 누락 시 실패")
    void invalidMandatoryFields() {
        BookCreateRequest request = new BookCreateRequest();

        Set<ConstraintViolation<BookCreateRequest>> violations = validator.validate(request);
        
        // ISBN, Title, Price 3가지 위반 발생 확인
        assertThat(violations).hasSizeGreaterThanOrEqualTo(3); 
        assertThat(violations).extracting(ConstraintViolation::getMessage)
                .contains(
                    "ISBN은 필수 입력 값입니다.",
                    "도서 제목은 필수 입력 값입니다.",
                    "가격은 필수 입력 값입니다."
                );
    }

    @Test
    @DisplayName("도서 생성 요청 - 가격이 음수일 때 실패")
    void invalidPrice() {
        BookCreateRequest request = new BookCreateRequest();
        request.setIsbn("123");
        request.setTitle("Title");
        request.setPrice(-100);

        Set<ConstraintViolation<BookCreateRequest>> violations = validator.validate(request);
        
        assertThat(violations).isNotEmpty();
        assertThat(violations.iterator().next().getMessage()).isEqualTo("가격은 0원 이상이어야 합니다.");
    }

    @Test
    @DisplayName("JSON 매핑 테스트 - @JsonProperty 동작 확인 (pubDate, imageUrl)")
    void jsonMappingTest() throws JsonProcessingException {
        // Given
        String json = """
                {
                    "isbn": "978-3-16-148410-0",
                    "title": "Test Book",
                    "price": 10000,
                    "publishedDate": "2023-12-25",
                    "imageUrl": "http://example.com/image.jpg"
                }
                """;

        // When
        BookCreateRequest request = objectMapper.readValue(json, BookCreateRequest.class);

        // Then
        assertThat(request.getIsbn()).isEqualTo("978-3-16-148410-0");
        assertThat(request.getPublishedDate()).isEqualTo("2023-12-25"); // pubDate -> publishedDate 매핑 확인
        assertThat(request.getImage()).isEqualTo("http://example.com/image.jpg"); // imageUrl -> image 매핑 확인
    }
}