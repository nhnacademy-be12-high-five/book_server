package com.nhnacademy.book_server.mapper;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

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

    @Test
    @DisplayName("getParentId: 대분류 ID(1~7)를 넣으면 0을 반환해야 한다")
    void getParentId_Parent_ReturnsZero() {
        assertThat(CategoryMapper.getParentId(1)).isEqualTo(0);
        assertThat(CategoryMapper.getParentId(7)).isEqualTo(0);
    }

    @Test
    @DisplayName("getParentId: 매핑되지 않은 ID가 들어오면 기본값(1)을 반환해야 한다")
    void getParentId_Unknown_ReturnsDefault() {
        assertThat(CategoryMapper.getParentId(999)).isEqualTo(1);
    }

    // 2. 제목 기반 카테고리 찾기 테스트 (다양한 케이스 검증)
    @ParameterizedTest
    @CsvSource({
            // 소분류 우선 매칭 테스트
            "'해리포터와 마법사의 돌', 8",    // 키워드: 판타지, 마법 -> 소설(8)
            "'자바의 정석', 10",            // 키워드: 자바 -> IT(10)
            "'맨큐의 경제학', 9",           // 키워드: 경제 -> 경제/경영(9)
            "'맛있는 파스타 요리', 11",      // 키워드: 요리 -> 인문/요리(11)
            "'토익 기출문제집', 13",        // 키워드: 토익 -> 수험서(13)

            // 대분류 매칭 테스트 (소분류 키워드가 없을 때)
            "'재미있는 과학 상식', 7",       // 키워드: 과학 -> 자연/과학(7) (만약 소분류에 '과학'이 없다고 가정 시)

            // 대소문자 무시 테스트
            "'JAVA Programming', 10",     // 영어 대문자 -> IT(10)
            "'Python 알고리즘', 10"        // 혼합 -> IT(10)
    })

    @DisplayName("findCategoryId: 제목에 포함된 키워드로 올바른 카테고리 ID를 찾아야 한다")
    void findCategoryId_Success(String title, int expectedId) {
        // When
        Integer result = CategoryMapper.findCategoryId(title);

        // Then
        assertThat(result).isEqualTo(expectedId);
    }

    // 3. 예외 케이스 테스트
    @Test
    @DisplayName("findCategoryId: 매칭되는 키워드가 없으면 null을 반환해야 한다")
    void findCategoryId_NoMatch() {
        String title = "아무런 키워드도 없는 쌩뚱맞은 제목";
        assertThat(CategoryMapper.findCategoryId(title)).isNull();
    }

    @Test
    @DisplayName("findCategoryId: 제목이 null이거나 빈 문자열이면 null을 반환해야 한다")
    void findCategoryId_NullOrEmpty() {
        assertThat(CategoryMapper.findCategoryId(null)).isNull();
        assertThat(CategoryMapper.findCategoryId("")).isNull();
        assertThat(CategoryMapper.findCategoryId("   ")).isNull(); // 공백만 있는 경우 매칭 안 됨
    }
}