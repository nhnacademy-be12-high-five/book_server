package com.nhnacademy.book_server.repository.review;
import com.nhnacademy.book_server.entity.Book;
import com.nhnacademy.book_server.entity.Publisher;
import com.nhnacademy.book_server.entity.Review;
import com.nhnacademy.book_server.entity.ReviewLike;
import com.nhnacademy.book_server.repository.BookRepository;
import com.nhnacademy.book_server.repository.PublisherRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class ReviewLikeRepositoryTest {

    @Autowired
    private ReviewLikeRepository reviewLikeRepository;
    @Autowired
    private ReviewRepository reviewRepository;
    @Autowired
    private BookRepository bookRepository;
    @Autowired
    private PublisherRepository publisherRepository;

    private Review review;
    private Long memberId = 100L;

    @BeforeEach
    void setUp() {
        Publisher publisher = Publisher.builder()
                .name("Pub")
                .build();
        publisherRepository.save(publisher);

        Book book = Book.builder()
                .title("Book")
                .isbn13("123")
                .price(100)
                .publisher(publisher)
                .content("Content")
                .publishedDate("2024-01-01")
                .salesVolume(0L)
                .averageRating(0.0) // [필수] 빌더 사용 시 기본값 0.0 설정
                .reviewCount(0)     // [필수] 빌더 사용 시 기본값 0 설정
                .build();
        bookRepository.save(book);

        review = new Review(5, "Good", book, 1L);
        reviewRepository.save(review);
    }

    @Test
    @DisplayName("멤버 ID와 리뷰 ID로 좋아요 조회")
    void findByMemberIdAndReviewIdTest() {
        ReviewLike like = new ReviewLike(review, memberId);
        reviewLikeRepository.save(like);

        Optional<ReviewLike> found = reviewLikeRepository.findByMemberIdAndReviewId(memberId, review.getId());

        assertThat(found).isPresent();
        assertThat(found.get().getMemberId()).isEqualTo(memberId);
    }

    @Test
    @DisplayName("특정 리뷰 목록 중 내가 좋아요한 리뷰 ID만 조회")
    void findReviewIdsByMemberIdAndReviewIdsTest() {
        Review review2 = new Review(3, "Bad", review.getBook(), 2L);
        reviewRepository.save(review2);

        ReviewLike like1 = new ReviewLike(review, memberId);
        reviewLikeRepository.save(like1);

        List<Long> targetReviewIds = List.of(review.getId(), review2.getId());

        List<Long> likedReviewIds = reviewLikeRepository.findReviewIdsByMemberIdAndReviewIds(memberId, targetReviewIds);

        assertThat(likedReviewIds).hasSize(1);
        assertThat(likedReviewIds).contains(review.getId());
        assertThat(likedReviewIds).doesNotContain(review2.getId());
    }
}