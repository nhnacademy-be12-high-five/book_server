package com.nhnacademy.book_server.parser;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ParsingDtoTest {

    @Test
    @DisplayName("ParsingDto: 기본 생성자 및 Getter/Setter 동작 테스트")
    void dtoGetterSetterTest() {
        // Given
        ParsingDto dto = new ParsingDto();

        // When
        dto.setIsbn("9791112345678");
        dto.setTitle("Test Book");
        dto.setAuthor("Hong Gil Dong");
        dto.setPublisher("Test Publisher");
        dto.setPubDate("2024-01-01");
        dto.setPrice("15000");
        dto.setImageUrl("http://image.url");
        dto.setDescription("Description");
        
        // 수동으로 정의된 메서드 테스트
        dto.setCategoryId(100);
        dto.setCategoryName("IT");

        // Then
        assertThat(dto.getIsbn()).isEqualTo("9791112345678");
        assertThat(dto.getTitle()).isEqualTo("Test Book");
        assertThat(dto.getAuthor()).isEqualTo("Hong Gil Dong");
        assertThat(dto.getPublisher()).isEqualTo("Test Publisher");
        assertThat(dto.getPubDate()).isEqualTo("2024-01-01");
        assertThat(dto.getPrice()).isEqualTo("15000");
        assertThat(dto.getImageUrl()).isEqualTo("http://image.url");
        assertThat(dto.getDescription()).isEqualTo("Description");
        
        // 수동 Getter 확인
        assertThat(dto.getCategoryId()).isEqualTo(100);
        assertThat(dto.getCategoryName()).isEqualTo("IT");
    }

    @Test
    @DisplayName("ParsingDto: @AllArgsConstructor 동작 테스트")
    void allArgsConstructorTest() {
        // Given
        String seq = "1";
        String isbn = "1234567890123";
        String title = "Java Programming";
        String author = "Author";
        String pub = "Pub";
        String date = "2023-01-01";
        String price = "30000";
        String img = "img.jpg";
        String desc = "Good Book";
        Integer catId = 1;
        String catName = "Tech";
        Integer parentId = 0;
        Integer depth = 1;

        // When
        ParsingDto dto = new ParsingDto(
            seq, isbn, title, author, pub, date, price, img, desc, 
            catId, catName, parentId, depth
        );

        // Then
        assertThat(dto.getTitle()).isEqualTo(title);
        assertThat(dto.getIsbn()).isEqualTo(isbn);
        assertThat(dto.getAuthor()).isEqualTo(author);
        assertThat(dto.getCategoryId()).isEqualTo(catId);
    }

    @Test
    @DisplayName("ParsingDto: toString 포함 여부 테스트")
    void toStringTest() {
        // Given
        ParsingDto dto = new ParsingDto();
        dto.setTitle("MyBook");
        dto.setIsbn("9999");

        // When
        String result = dto.toString();

        // Then
        assertThat(result).contains("MyBook", "9999", "ParsingDto");
    }
}