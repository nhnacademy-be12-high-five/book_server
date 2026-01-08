package com.nhnacademy.book_server.dto.common;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RestPageTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    @DisplayName("JSON 역직렬화 테스트 - @JsonCreator를 통해 JSON이 객체로 변환되는지 확인")
    void jsonDeserializationTest() throws JsonProcessingException {
        // Given
        // 실제 PageImpl을 직렬화했을 때와 유사한 형태의 JSON 문자열
        String json = """
                {
                    "content": ["item1", "item2"],
                    "number": 0,
                    "size": 10,
                    "totalElements": 2,
                    "pageable": "INSTANCE", 
                    "last": true,
                    "totalPages": 1,
                    "sort": [],
                    "first": true,
                    "numberOfElements": 2
                }
                """;

        // When
        // TypeReference를 사용하여 제네릭 타입(RestPage<String>)까지 정확하게 명시
        RestPage<String> page = objectMapper.readValue(json, new TypeReference<RestPage<String>>() {});

        // Then
        assertThat(page.getContent()).containsExactly("item1", "item2"); // 내용 확인
        assertThat(page.getNumber()).isZero();  // 페이지 번호
        assertThat(page.getSize()).isEqualTo(10);   // 페이지 크기
        assertThat(page.getTotalElements()).isEqualTo(2); // 전체 요소 수
        
        // 부가 정보 확인 (RestPage 생성자 내부에서 PageRequest.of(number, size)로 재구성됨)
        assertThat(page.isFirst()).isTrue();
    }

    @Test
    @DisplayName("생성자 테스트 - Page 객체 변환")
    void constructorFromPageTest() {
        // Given
        List<String> content = List.of("A", "B");
        Pageable pageable = PageRequest.of(0, 5);
        Page<String> originalPage = new PageImpl<>(content, pageable, 10);

        // When
        RestPage<String> restPage = new RestPage<>(originalPage);

        // Then
        assertThat(restPage.getContent()).isEqualTo(content);
        assertThat(restPage.getPageable()).isEqualTo(pageable);
        assertThat(restPage.getTotalElements()).isEqualTo(10);
    }

    @Test
    @DisplayName("생성자 테스트 - content, pageable, total 직접 주입")
    void constructorFromArgsTest() {
        // Given
        List<String> content = List.of("X", "Y");
        Pageable pageable = PageRequest.of(1, 2); // 1페이지, 사이즈 2
        long total = 100;

        // When
        RestPage<String> restPage = new RestPage<>(content, pageable, total);

        // Then
        assertThat(restPage.getContent()).isEqualTo(content);
        assertThat(restPage.getNumber()).isEqualTo(1);
        assertThat(restPage.getSize()).isEqualTo(2);
        assertThat(restPage.getTotalElements()).isEqualTo(total);
    }

    @Test
    @DisplayName("생성자 테스트 - content만 주입 (Pageable 미사용)")
    void constructorFromContentTest() {
        // Given
        List<String> content = List.of("Hello");

        // When
        RestPage<String> restPage = new RestPage<>(content);

        // Then
        assertThat(restPage.getContent()).isEqualTo(content);
        assertThat(restPage.getTotalElements()).isEqualTo(1); // content 크기가 total이 됨
    }

    @Test
    @DisplayName("생성자 테스트 - 기본 생성자 (NoArgs)")
    void noArgsConstructorTest() {
        // When
        RestPage<String> restPage = new RestPage<>();

        // Then
        assertThat(restPage.getContent()).isEmpty();
        assertThat(restPage.getTotalElements()).isZero();
    }
}