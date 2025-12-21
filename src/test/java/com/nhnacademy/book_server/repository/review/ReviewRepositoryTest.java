package com.nhnacademy.book_server.repository.review;

import com.nhnacademy.book_server.entity.Book;
import com.nhnacademy.book_server.entity.Publisher;
import com.nhnacademy.book_server.entity.Review;
import com.nhnacademy.book_server.repository.BookRepository;
import com.nhnacademy.book_server.repository.PublisherRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.ANY)
@ImportAutoConfiguration(exclude = {
        org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration.class,
        org.springframework.boot.autoconfigure.liquibase.LiquibaseAutoConfiguration.class
})
class ReviewRepositoryTest {

    @Autowired
    private ReviewRepository reviewRepository;
    @Autowired
    private BookRepository bookRepository;
    @Autowired
    private PublisherRepository publisherRepository;
    @Autowired
    private TestEntityManager entityManager;

    private Book testBook;
    private Long memberId = 1L;

    @BeforeEach
    void setUp() {
        Publisher publisher = Publisher.builder()
                .name("Test Publisher")
                .build();
        publisherRepository.save(publisher);

        testBook = Book.builder()
                .title("Test Book")
                .isbn13("1234567890123")
                .price(10000)
                .publisher(publisher)
                .content("Description")
                .publishedDate("2024-01-01")
                .salesVolume(0L)
                .averageRating(0.0) // [필수]
                .reviewCount(0)     // [필수]
                .build();
        bookRepository.save(testBook);
    }

    @Test
    @DisplayName("책 ID로 리뷰 목록 조회 (페이징)")
    void findByBookIdTest() {
        Review review1 = new Review(5, "Content 1", testBook, memberId);
        Review review2 = new Review(4, "Content 2", testBook, memberId + 1);
        reviewRepository.saveAll(List.of(review1, review2));

        Page<Review> result = reviewRepository.findByBookId(testBook.getId(), PageRequest.of(0, 10));

        assertThat(result.getContent()).hasSize(2);
    }

    @Test
    @DisplayName("좋아요 카운트 증가/감소 (JPQL Modifying)")
    void likeCountUpdateTest() {
        Review review = new Review(5, "Like Test", testBook, memberId);
        review = reviewRepository.save(review);
        Long reviewId = review.getId();

        // 증가
        reviewRepository.increaseLikeCount(reviewId);
        entityManager.clear();

        Review updated = reviewRepository.findById(reviewId).orElseThrow();
        assertThat(updated.getLikeCount()).isEqualTo(1);

        // 감소
        reviewRepository.decreaseLikeCount(reviewId);
        entityManager.clear();

        updated = reviewRepository.findById(reviewId).orElseThrow();
        assertThat(updated.getLikeCount()).isEqualTo(0);
    }

    @Test
    @DisplayName("책의 평균 평점 계산")
    void getAverageRatingTest() {
        Review r1 = new Review(5, "5점", testBook, 1L);
        Review r2 = new Review(3, "3점", testBook, 2L);
        reviewRepository.saveAll(List.of(r1, r2));

        Double avg = reviewRepository.getAverageRating(testBook.getId());

        assertThat(avg).isEqualTo(4.0);
    }

    // 나머지 테스트 메서드 생략 (기존과 동일)
}