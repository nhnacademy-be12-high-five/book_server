package com.nhnacademy.book_server.mapper;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CategoryMapperTest {

    @Test
    @DisplayName("getParentId: 소분류 ID를 넣으면 매핑된 대분류 ID를 반환해야 한다")
    void getParentId_Child_ReturnsParent() {
        // Given & When & Then
        assertThat(CategoryMapper.getParentId(8)).isEqualTo(1);  // 소설 -> 문학
        assertThat(CategoryMapper.getParentId(10)).isEqualTo(3); // IT -> IT
        assertThat(CategoryMapper.getParentId(14)).isEqualTo(7); // 공학 -> 과학
    }
//
    @Test
    @DisplayName("getParentId: 대분류 ID(1~7)를 넣으면 0을 반환해야 한다")
    void getParentId_Parent_ReturnsZero() {
        assertThat(CategoryMapper.getParentId(1)).isZero();
        assertThat(CategoryMapper.getParentId(7)).isZero();
    }
//
    @Test
    @DisplayName("getParentId: 매핑되지 않은 ID가 들어오면 기본값(1)을 반환해야 한다")
    void getParentId_Unknown_ReturnsDefault() {
        assertThat(CategoryMapper.getParentId(999)).isEqualTo(1);
    }

    // 3. 예외 케이스 테스트
    @Test
    @DisplayName("findCategoryId: 매칭되는 키워드가 없으면 null을 반환해야 한다")
    void findCategoryId_NoMatch() {
        String title = "아무런 키워드도 없는 쌩뚱맞은 제목";
        assertThat(CategoryMapper.findCategoryId(title)).isNull();
    }
//
    @Test
    @DisplayName("findCategoryId: 제목이 null이거나 빈 문자열이면 null을 반환해야 한다")
    void findCategoryId_NullOrEmpty() {
        assertThat(CategoryMapper.findCategoryId(null)).isNull();
        assertThat(CategoryMapper.findCategoryId("")).isNull();
        assertThat(CategoryMapper.findCategoryId("   ")).isNull(); // 공백만 있는 경우 매칭 안 됨
    }
}