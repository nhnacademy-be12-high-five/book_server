package com.nhnacademy.book_server.service.search;

import com.nhnacademy.book_server.entity.Book;
import com.nhnacademy.book_server.entity.Review;
import com.nhnacademy.book_server.repository.BookRepository;
import com.nhnacademy.book_server.repository.review.ReviewRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = BookReindexService.class)
class BookReindexServiceTest {

    @Autowired
    BookReindexService service;

    @MockitoBean BookRepository bookRepository;
    @MockitoBean ReviewRepository reviewRepository;
    @MockitoBean ElasticService elasticService;

    @Test
    @DisplayName("reindexAll: 도서 0권이면 0 반환")
    void reindex_zero_books() {
        when(bookRepository.count()).thenReturn(0L);

        long result = service.reindexAll();

        assertThat(result).isZero();
        verifyNoInteractions(elasticService);
    }

    @Test
    @DisplayName("reindexAll: 1페이지 인덱싱 성공")
    void reindex_one_page() {
        Book book = mock(Book.class);
        when(book.getId()).thenReturn(1L);

        when(bookRepository.count()).thenReturn(1L);
        when(bookRepository.findAll(PageRequest.of(0, 1000)))
                .thenReturn(new PageImpl<>(List.of(book)));

        Review review = mock(Review.class);
        when(review.getBook()).thenReturn(book);
        when(reviewRepository.findByBookIdIn(List.of(1L)))
                .thenReturn(List.of(review));

        long result = service.reindexAll();

        assertThat(result).isEqualTo(1L);
        verify(elasticService).saveAll(any(List.class));
    }
}
