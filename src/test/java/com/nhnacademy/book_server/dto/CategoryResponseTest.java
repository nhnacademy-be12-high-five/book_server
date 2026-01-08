package com.nhnacademy.book_server.dto;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CategoryResponseTest {

    @Test
    @DisplayName("CategoryResponse 생성 및 필드 조회 테스트")
    void createCategoryResponse() {
        // Given
        int id = 10;
        String name = "Domestic";

        // When
        CategoryResponse response = new CategoryResponse(id, name);

        // Then
        assertThat(response).isNotNull();
        assertThat(response.categoryId()).isEqualTo(id);
        assertThat(response.categoryName()).isEqualTo(name);
    }

    @Test
    @DisplayName("CategoryResponse 동등성(Equality) 테스트")
    void verifyEqualsAndHashCode() {
        // Given
        CategoryResponse response1 = new CategoryResponse(1, "Fiction");
        CategoryResponse response2 = new CategoryResponse(1, "Fiction");
        CategoryResponse response3 = new CategoryResponse(2, "Non-Fiction");

        // Then
        // 1. 내용이 같으면 같은 객체로 취급 (record 특성)
        assertThat(response1).isEqualTo(response2);
        assertThat(response1.hashCode()).hasSameHashCodeAs(response2.hashCode());

        // 2. 내용이 다르면 다른 객체
        assertThat(response1).isNotEqualTo(response3);
    }

    @Test
    @DisplayName("CategoryResponse toString 포함 여부 테스트")
    void verifyToString() {
        // Given
        CategoryResponse response = new CategoryResponse(5, "Science");

        // When
        String toString = response.toString();

        // Then
        assertThat(toString).contains("CategoryResponse", "categoryId=5", "categoryName=Science");
    }
}