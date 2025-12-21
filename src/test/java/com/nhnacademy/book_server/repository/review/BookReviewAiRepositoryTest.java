package com.nhnacademy.book_server.repository.review;

import com.nhnacademy.book_server.entity.Book;
import com.nhnacademy.book_server.entity.BookReviewAi;
import com.nhnacademy.book_server.entity.Publisher;
import com.nhnacademy.book_server.repository.BookRepository;
import com.nhnacademy.book_server.repository.PublisherRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.ANY)
@ImportAutoConfiguration(exclude = {
        org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration.class,
        org.springframework.boot.autoconfigure.liquibase.LiquibaseAutoConfiguration.class
})
class BookReviewAiRepositoryTest {

    @Autowired
    private BookReviewAiRepository bookReviewAiRepository;
    @Autowired
    private BookRepository bookRepository;
    @Autowired
    private PublisherRepository publisherRepository;

    @Test
    @DisplayName("책 ID로 AI 요약 정보 조회")
    void findByBook_IdTest() {
        Publisher pub = Publisher.builder().name("Pub").build();
        publisherRepository.save(pub);

        Book book = Book.builder()
                .title("AI Book")
                .isbn13("999")
                .price(100)
                .publisher(pub)
                .content("Content")
                .publishedDate("2024-01-01")
                .salesVolume(0L)
                .averageRating(0.0) // [필수]
                .reviewCount(0)     // [필수]
                .build();
        bookRepository.save(book);

        BookReviewAi aiData = new BookReviewAi(book, "AI Summary", 10L, 4.5);
        bookReviewAiRepository.save(aiData);

        Optional<BookReviewAi> result = bookReviewAiRepository.findByBook_Id(book.getId());

        assertThat(result).isPresent();
        assertThat(result.get().getSummary()).isEqualTo("AI Summary");
        assertThat(result.get().getBook().getId()).isEqualTo(book.getId());
    }
}