package com.nhnacademy.book_server.entity.book;

import com.nhnacademy.book_server.entity.Book;
import com.nhnacademy.book_server.entity.BookCategory;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class BookTest {

    private Validator validator;

    @BeforeEach
    void setUp() {
        ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @Test
    @DisplayName("Builder를 사용한 Book 생성 및 기본값(@Builder.Default) 테스트")
    void createBook_BuilderDefaults() {
        // Given
        Book book = Book.builder()
                .isbn13("9791112345678")
                .title("테스트 도서")
                .price(15000)
                .content("이것은 테스트 도서의 내용입니다.")
                .publishedDate("2024-01-01")
                .build();

        // Then
        // 1. 입력한 필드 검증
        assertThat(book.getIsbn13()).isEqualTo("9791112345678");
        assertThat(book.getTitle()).isEqualTo("테스트 도서");
        assertThat(book.getPrice()).isEqualTo(15000);

        // 2. @Builder.Default로 설정된 기본값 검증
        assertThat(book.getReviewCount()).isZero();
        assertThat(book.getAverageRating()).isEqualTo(0.0);
        assertThat(book.getSalesVolume()).isZero();

        // 3. 리스트 초기화 검증 (@Builder.Default 있음)
        assertThat(book.getBookAuthors()).isNotNull().isEmpty();
        assertThat(book.getBookCategories()).isNotNull().isEmpty();

        // 4. 주의: bookTags는 @Builder.Default가 없으므로 Builder 사용 시 null일 수 있음
        // 만약 null이 아니어야 한다면 엔티티에 @Builder.Default를 추가해야 함
        assertThat(book.getBookTags()).isNull(); 
    }

    @Test
    @DisplayName("기본 생성자(new)로 Book 생성 시 리스트 초기화 테스트")
    void createBook_NoArgsConstructor() {
        // Given
        Book book = new Book();

        // Then
        // 기본 생성자는 필드 선언부의 = new ArrayList<>()를 실행하므로 모두 null이 아님
        assertThat(book.getBookAuthors()).isNotNull().isEmpty();
        assertThat(book.getBookCategories()).isNotNull().isEmpty();
        assertThat(book.getBookTags()).isNotNull().isEmpty();
    }

    @Test
    @DisplayName("Validation 테스트: 필수 필드(@NotNull) 누락 시 검증 실패")
    void validate_NotNullFields() {
        // Given: 아무 필드도 설정하지 않은 빈 객체
        Book book = Book.builder().build();

        // When
        Set<ConstraintViolation<Book>> violations = validator.validate(book);

        // Then
        // isbn13, title, price, content, publishedDate 필드에 @NotNull 존재 확인
        assertThat(violations).extracting(ConstraintViolation::getPropertyPath)
                .map(Object::toString)
                .containsExactlyInAnyOrder(
                        "isbn13", 
                        "title", 
                        "price", 
                        "content", 
                        "publishedDate"
                );
    }

    @Test
    @DisplayName("setStock 메서드 동작 테스트")
    void setStockTest() {
        // Given
        Book book = new Book();
        Integer newStock = 50;

        // When
        book.setStock(newStock);

        // Then
        assertThat(book.getStock()).isEqualTo(newStock);
    }

    @Test
    @DisplayName("연관관계 리스트 조작 테스트 (BookCategory 추가)")
    void listManipulationTest() {
        // Given
        Book book = new Book();
        BookCategory category = new BookCategory(); // 가짜 카테고리 객체

        // When
        book.getBookCategories().add(category);

        // Then
        assertThat(book.getBookCategories()).hasSize(1);
        assertThat(book.getBookCategories().get(0)).isEqualTo(category);
    }
}