package com.nhnacademy.book_server.dto.book;

import com.nhnacademy.book_server.dto.response.LikedBookResponse;
import com.nhnacademy.book_server.entity.Author;
import com.nhnacademy.book_server.entity.Book;
import com.nhnacademy.book_server.entity.BookAuthor;
import com.nhnacademy.book_server.entity.Publisher;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

@ExtendWith(MockitoExtension.class)
class LikedBookResponseTest {

    @Mock
    private Book book;

    @Mock
    private Publisher publisher;

    @Test
    @DisplayName("from(Book): 단일 저자 및 모든 필드 정상 변환 테스트")
    void from_SingleAuthor() {
        // Given
        Long bookId = 1L;
        String title = "Test Title";
        String imageUrl = "http://test.com/image.jpg";
        int price = 15000;
        String authorName = "Kim Author";
        String publisherName = "Test Pub";

        // Mock 설정
        given(book.getId()).willReturn(bookId);
        given(book.getTitle()).willReturn(title);
        given(book.getImage()).willReturn(imageUrl);
        given(book.getPrice()).willReturn(price);
        
        // 출판사 설정
        given(book.getPublisher()).willReturn(publisher);
        given(publisher.getName()).willReturn(publisherName);

        // 저자 설정 (1명)
        Author author = mock(Author.class);
        given(author.getName()).willReturn(authorName);
        BookAuthor bookAuthor = mock(BookAuthor.class);
        given(bookAuthor.getAuthor()).willReturn(author);
        given(book.getBookAuthors()).willReturn(List.of(bookAuthor));

        // When
        LikedBookResponse response = LikedBookResponse.from(book);

        // Then
        assertThat(response.getBookId()).isEqualTo(bookId);
        assertThat(response.getTitle()).isEqualTo(title);
        assertThat(response.getThumbnailUrl()).isEqualTo(imageUrl);
        assertThat(response.getPrice()).isEqualTo(price);
        
        assertThat(response.getPublisher()).isEqualTo(publisherName);
        assertThat(response.getAuthor()).isEqualTo(authorName); // 저자가 1명이면 이름 그대로

        // 기본값 확인
        assertThat(response.isFreeShipping()).isFalse();
        assertThat(response.isTodayShipping()).isFalse();
    }

    @Test
    @DisplayName("from(Book): 여러 저자일 경우 콤마(,)로 연결되는지 확인")
    void from_MultipleAuthors() {
        // Given
        Author a1 = mock(Author.class); given(a1.getName()).willReturn("Author1");
        BookAuthor ba1 = mock(BookAuthor.class); given(ba1.getAuthor()).willReturn(a1);

        Author a2 = mock(Author.class); given(a2.getName()).willReturn("Author2");
        BookAuthor ba2 = mock(BookAuthor.class); given(ba2.getAuthor()).willReturn(a2);

        given(book.getBookAuthors()).willReturn(List.of(ba1, ba2));
        
        // Null 방지용 Stub
        given(book.getPublisher()).willReturn(null); 

        // When
        LikedBookResponse response = LikedBookResponse.from(book);

        // Then
        // 순서는 List 순서를 따름. "Author1, Author2" 확인
        assertThat(response.getAuthor()).contains("Author1", "Author2", ", ");
        assertThat(response.getAuthor()).isEqualTo("Author1, Author2");
    }

    @Test
    @DisplayName("from(Book): 저자와 출판사가 없을 때 빈 문자열 처리 확인")
    void from_NullSafety() {
        // Given
        given(book.getId()).willReturn(10L);
        // 저자 리스트 Null
        given(book.getBookAuthors()).willReturn(null);
        // 출판사 Null
        given(book.getPublisher()).willReturn(null);

        // When
        LikedBookResponse response = LikedBookResponse.from(book);

        // Then
        assertThat(response.getAuthor()).isEmpty();     // "" 반환 확인
        assertThat(response.getPublisher()).isEmpty();  // "" 반환 확인
    }

    @Test
    @DisplayName("from(Book): 저자 리스트가 비어있을 때 빈 문자열 처리 확인")
    void from_EmptyAuthorList() {
        // Given
        given(book.getId()).willReturn(10L);
        given(book.getBookAuthors()).willReturn(Collections.emptyList()); // Empty List
        given(book.getPublisher()).willReturn(null);

        // When
        LikedBookResponse response = LikedBookResponse.from(book);

        // Then
        assertThat(response.getAuthor()).isEmpty();
    }
}