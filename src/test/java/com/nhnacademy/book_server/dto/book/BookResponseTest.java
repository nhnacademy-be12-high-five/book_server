package com.nhnacademy.book_server.dto.book;

import com.nhnacademy.book_server.dto.BookResponse;
import com.nhnacademy.book_server.entity.*;
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
class BookResponseTest {

    @Mock
    private Book book;

    @Mock
    private Publisher publisher;

    @Test
    @DisplayName("fromWithReviewSummary: 리뷰 리스트로 평점 계산 및 AI 요약 포함 확인")
    void fromWithReviewSummary_CalculatesAverageAndIncludesSummary() {
        // Given
        String reviewSummary = "AI Review Summary";
        Review r1 = mock(Review.class);
        given(r1.getRating()).willReturn(5);
        Review r2 = mock(Review.class);
        given(r2.getRating()).willReturn(3);
        List<Review> reviews = List.of(r1, r2); // Avg: 4.0

        setupBasicBookMocks();

        // When
        BookResponse response = BookResponse.fromWithReviewSummary(book, reviewSummary, reviews);

        // Then
        assertThat(response.avgRating()).isEqualTo(4.0);
        assertThat(response.reviewCount()).isEqualTo(2L);
        assertThat(response.aiReviewSummary()).isEqualTo(reviewSummary);
    }

    @Test
    @DisplayName("fromWithReviewSummary: 리뷰가 없을 때 평점 0.0 처리")
    void fromWithReviewSummary_NoReviews() {
        // Given
        setupBasicBookMocks();

        // When
        BookResponse response = BookResponse.fromWithReviewSummary(book, "Summary", Collections.emptyList());

        // Then
        assertThat(response.avgRating()).isEqualTo(0.0);
        assertThat(response.reviewCount()).isZero();
    }

    @Test
    @DisplayName("fromWithAiSummary: AI 책 요약(aiSummary) 포함 확인")
    void fromWithAiSummary_IncludesAiSummary() {
        // Given
        setupBasicBookMocks();
        String aiSummary = "This is a book summary.";

        // When
        BookResponse response = BookResponse.fromWithAiSummary(book, Collections.emptyList(), 4.5, 10L, aiSummary);

        // Then
        assertThat(response.aiSummary()).isEqualTo(aiSummary);
        assertThat(response.avgRating()).isEqualTo(4.5);
    }

    @Test
    @DisplayName("from (Review List): 리뷰 리스트 평점 계산 확인")
    void from_ReviewList_CalculatesAverage() {
        // Given
        setupBasicBookMocks();
        Review r1 = mock(Review.class);
        given(r1.getRating()).willReturn(5);
        List<Review> reviews = List.of(r1);

        // When
        BookResponse response = BookResponse.from(book, Collections.emptyList(), reviews);

        // Then
        assertThat(response.avgRating()).isEqualTo(5.0);
        assertThat(response.reviewCount()).isEqualTo(1L);
    }

    @Test
    @DisplayName("from (Entity): Book 엔티티 자체 평점/리뷰수 사용 확인")
    void from_Entity_UsesInternalStats() {
        // Given
        setupBasicBookMocks();
        given(book.getAverageRating()).willReturn(4.8);
        given(book.getReviewCount()).willReturn(123);

        // When
        BookResponse response = BookResponse.from(book);

        // Then
        assertThat(response.avgRating()).isEqualTo(4.8);
        assertThat(response.reviewCount()).isEqualTo(123L);
    }

    @Test
    @DisplayName("작가 정보 변환: 여러 작가 이름 콤마(,) 연결 및 중복 제거 확인")
    void build_AuthorNames() {
        // Given
        Author a1 = mock(Author.class); given(a1.getName()).willReturn("Author A");
        Author a2 = mock(Author.class); given(a2.getName()).willReturn("Author B");
        Author a3 = mock(Author.class); given(a3.getName()).willReturn("Author A"); // 중복

        BookAuthor ba1 = mock(BookAuthor.class); given(ba1.getAuthor()).willReturn(a1);
        BookAuthor ba2 = mock(BookAuthor.class); given(ba2.getAuthor()).willReturn(a2);
        BookAuthor ba3 = mock(BookAuthor.class); given(ba3.getAuthor()).willReturn(a3);

        given(book.getBookAuthors()).willReturn(List.of(ba1, ba2, ba3));
        given(book.getPublisher()).willReturn(publisher);
        given(publisher.getName()).willReturn("Pub");

        // When
        BookResponse response = BookResponse.from(book);

        // Then
        assertThat(response.author()).contains("Author A", "Author B");
        // 순서는 스트림 처리에 따라 달라질 수 있으나, 보통 입력 순서 유지 (Distinct)
        // "Author A, Author B" 형태인지 확인
        assertThat(response.author()).isEqualTo("Author A, Author B");
    }

    @Test
    @DisplayName("카테고리 변환: CategoryResponse 매핑 및 대표 카테고리 ID 설정 확인")
    void build_CategoryMapping() {
        // Given
        Category c1 = mock(Category.class);
        given(c1.getCategoryId()).willReturn(10);
        given(c1.getCategoryName()).willReturn("Fiction");
        given(c1.getParentId()).willReturn(1); // Parent 존재

        Category c2 = mock(Category.class);
        given(c2.getCategoryId()).willReturn(20);
        given(c2.getCategoryName()).willReturn("Novel");

        BookCategory bc1 = mock(BookCategory.class); given(bc1.getCategory()).willReturn(c1);
        BookCategory bc2 = mock(BookCategory.class); given(bc2.getCategory()).willReturn(c2);

        setupBasicBookMocks();

        // When
        // bookCategories를 명시적으로 전달하는 메서드 사용
        BookResponse response = BookResponse.from(book, List.of(bc1, bc2));

        // Then
        assertThat(response.categories()).hasSize(2);
        assertThat(response.categories().get(0).categoryName()).isEqualTo("Fiction");
        
        // 첫 번째 카테고리가 Main으로 설정되는지 확인
        assertThat(response.categoryId()).isEqualTo(10);
        assertThat(response.parentId()).isEqualTo(1);
    }

    @Test
    @DisplayName("태그 변환: TagResponse 매핑 확인")
    void build_TagMapping() {
        // Given
        Tag t1 = mock(Tag.class);
        given(t1.getTagId()).willReturn(100L);
        given(t1.getName()).willReturn("BestSeller");

        BookTag bt1 = mock(BookTag.class);
        given(bt1.getTag()).willReturn(t1);

        given(book.getBookTags()).willReturn(List.of(bt1));
        setupBasicBookMocks(); // 나머지 필드 Mock 설정

        // When
        BookResponse response = BookResponse.from(book);

        // Then
        assertThat(response.tags()).hasSize(1);
        assertThat(response.tags().get(0).tagId()).isEqualTo(100L);
        assertThat(response.tags().get(0).name()).isEqualTo("BestSeller");
    }

    @Test
    @DisplayName("Null Safety: 필수 리스트(작가, 태그 등)가 Null일 때 에러 없이 빈 리스트/Null 처리")
    void build_NullSafety() {
        // Given
        // Book의 Getter들이 null을 반환하도록 설정 (Mockito 기본값이 null이거나 empty가 아닐 수 있음)
        given(book.getId()).willReturn(1L);
        given(book.getBookAuthors()).willReturn(null); // 작가 리스트 Null
        given(book.getBookTags()).willReturn(null);    // 태그 리스트 Null
        given(book.getPublisher()).willReturn(null);   // 출판사 Null
        given(book.getAverageRating()).willReturn(0.0);
        given(book.getReviewCount()).willReturn(0);
        
        // When
        BookResponse response = BookResponse.from(book);

        // Then
        assertThat(response.author()).isNull();
        assertThat(response.publisher()).isNull();
        assertThat(response.tags()).isEmpty(); // 빈 리스트 반환 확인
        assertThat(response.categories()).isEmpty();
    }

    // --- Helper Method ---
    private void setupBasicBookMocks() {
        given(book.getId()).willReturn(1L);
        given(book.getTitle()).willReturn("Test Title");
        given(book.getIsbn13()).willReturn("978-1234567890");
        given(book.getPrice()).willReturn(10000);
        given(book.getPublishedDate()).willReturn("2024-01-01");
        given(book.getPublisher()).willReturn(publisher);
        given(publisher.getName()).willReturn("Test Publisher");
        
        // 리스트들은 기본적으로 빈 리스트 반환 (Null Pointer 방지)
        // 필요한 테스트 메서드에서 override 가능
        if (book.getBookAuthors() == null) given(book.getBookAuthors()).willReturn(Collections.emptyList());
        if (book.getBookTags() == null) given(book.getBookTags()).willReturn(Collections.emptyList());
    }
}