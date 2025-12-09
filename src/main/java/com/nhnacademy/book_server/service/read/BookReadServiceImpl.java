package com.nhnacademy.book_server.service.read;

import com.nhnacademy.book_server.dto.BookResponse;
import com.nhnacademy.book_server.entity.Book;
import com.nhnacademy.book_server.entity.Review;
import com.nhnacademy.book_server.repository.BookRepository;
import com.nhnacademy.book_server.repository.ReviewRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class BookReadServiceImpl implements BookReadService {

    private final BookRepository bookRepository;
    private final ReviewRepository reviewRepository;

    // 전체 도서 조회
    @Override
    public List<BookResponse> findAllBooks() {
        List<Book> books = bookRepository.findAll();

        return books.stream()
                .map(book -> {
                    // 해당 도서의 리뷰 전체 조회 (페이징 없이)
                    List<Review> reviews = reviewRepository
                            .findByBookId(book.getId(), Pageable.unpaged())
                            .getContent();

                    // 카테고리는 여기서는 신경 안 씀 → null
                    return BookResponse.from(book, null, reviews);
                })
                .toList();
    }

    // 도서 ID 1건 조회
    @Override
    public Optional<BookResponse> findBookById(Long id) {
        return bookRepository.findById(id)
                .map(book -> {
                    List<Review> reviews = reviewRepository
                            .findByBookId(id, Pageable.unpaged())
                            .getContent();

                    return BookResponse.from(book, null, reviews);
                });
    }
}
