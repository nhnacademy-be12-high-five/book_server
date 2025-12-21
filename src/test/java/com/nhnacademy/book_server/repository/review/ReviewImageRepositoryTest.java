package com.nhnacademy.book_server.repository.review;
import com.nhnacademy.book_server.entity.Book;
import com.nhnacademy.book_server.entity.Publisher;
import com.nhnacademy.book_server.entity.Review;
import com.nhnacademy.book_server.entity.ReviewImage;
import com.nhnacademy.book_server.repository.BookRepository;
import com.nhnacademy.book_server.repository.PublisherRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class ReviewImageRepositoryTest {

    @Autowired
    private ReviewImageRepository reviewImageRepository;
    @Autowired
    private ReviewRepository reviewRepository;
    @Autowired
    private BookRepository bookRepository;
    @Autowired
    private PublisherRepository publisherRepository;

    @Test
    @DisplayName("리뷰 이미지 저장 및 조회")
    void saveAndFindTest() {
        Publisher pub = Publisher.builder().name("Pub").build();
        publisherRepository.save(pub);

        Book book = Book.builder()
                .title("Img Book")
                .isbn13("888")
                .price(100)
                .publisher(pub)
                .content("Content")
                .publishedDate("2024-01-01")
                .salesVolume(0L)
                .averageRating(0.0) // [필수]
                .reviewCount(0)     // [필수]
                .build();
        bookRepository.save(book);

        Review review = new Review(5, "Img Review", book, 1L);
        reviewRepository.save(review);

        ReviewImage image = new ReviewImage(review, "http://image.url/1.jpg");

        ReviewImage savedImage = reviewImageRepository.save(image);

        assertThat(savedImage.getId()).isNotNull();
        assertThat(savedImage.getFileUrl()).isEqualTo("http://image.url/1.jpg");
    }
}