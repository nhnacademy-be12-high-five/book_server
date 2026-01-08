package com.nhnacademy.book_server.entity.book;

import com.nhnacademy.book_server.entity.Author;
import com.nhnacademy.book_server.entity.Book;
import com.nhnacademy.book_server.entity.BookAuthor;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class BookAuthorTest {

    @Test
    @DisplayName("BookAuthor 생성 및 연관관계 매핑 테스트")
    void createBookAuthor() {
        // Given
        Book book = Book.builder().id(1L).title("Test Book").build();
        Author author = Author.builder().id(10L).name("Test Author").build();

        // When
        BookAuthor bookAuthor = new BookAuthor();
        bookAuthor.setBook(book);
        bookAuthor.setAuthor(author);

        // Then
        assertThat(bookAuthor.getBook()).isEqualTo(book);
        assertThat(bookAuthor.getAuthor()).isEqualTo(author);
        assertThat(bookAuthor.getId()).isNotNull(); // 기본 생성자에서 new Pk()로 초기화됨
    }

    @Test
    @DisplayName("Builder 패턴을 이용한 생성 테스트")
    void builderTest() {
        // Given
        Book book = Book.builder().id(1L).build();
        Author author = Author.builder().id(10L).build();

        // When
        BookAuthor bookAuthor = BookAuthor.builder()
                .book(book)
                .author(author)
                .build();

        // Then
        assertThat(bookAuthor.getBook()).isEqualTo(book);
        assertThat(bookAuthor.getAuthor()).isEqualTo(author);
    }

    @Test
    @DisplayName("Persistable 인터페이스: isNew()는 항상 true 반환")
    void isNew_AlwaysTrue() {
        // Given
        BookAuthor bookAuthor = new BookAuthor();

        // When & Then
        // BookAuthor는 복합키 매핑 특성상 merge 호출을 유도하기 위해 항상 true를 반환하도록 설계됨
        assertThat(bookAuthor.isNew()).isTrue();
    }

    @Test
    @DisplayName("복합키(Pk) 동등성(Equals/HashCode) 테스트")
    void pkEqualsAndHashCode() {
        // Given
        // Pk는 bookId와 authorId를 가짐
        BookAuthor.Pk pk1 = new BookAuthor.Pk(1L, 100L);
        BookAuthor.Pk pk2 = new BookAuthor.Pk(1L, 100L); // pk1과 데이터 동일
        BookAuthor.Pk pk3 = new BookAuthor.Pk(2L, 100L); // 다름

        // Then
        // 1. 같은 값을 가진 Pk 객체는 같아야 함 (Lombok @EqualsAndHashCode)
        assertThat(pk1).isEqualTo(pk2);
        assertThat(pk1.hashCode()).hasSameHashCodeAs(pk2.hashCode());

        // 2. 값이 다르면 달라야 함
        assertThat(pk1).isNotEqualTo(pk3);
    }

    @Test
    @DisplayName("Pk Getter/Setter 테스트")
    void pkGetterSetter() {
        // Given
        BookAuthor.Pk pk = new BookAuthor.Pk();

        // When
        pk.setBook(5L);
        pk.setAuthor(20L);

        // Then
        assertThat(pk.getBook()).isEqualTo(5L);
        assertThat(pk.getAuthor()).isEqualTo(20L);
    }
}