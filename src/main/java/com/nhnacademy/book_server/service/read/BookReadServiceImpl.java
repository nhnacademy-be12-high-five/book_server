package com.nhnacademy.book_server.service.read;

import com.nhnacademy.book_server.entity.Book;
import com.nhnacademy.book_server.dto.BookResponse;
import com.nhnacademy.book_server.entity.BookReviewAi;
import com.nhnacademy.book_server.entity.Review;
import com.nhnacademy.book_server.repository.BookRepository;
import com.nhnacademy.book_server.repository.BookReviewAiRepository;
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
    private final BookReviewAiRepository bookReviewAiRepository;

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
    public Optional<BookResponse> findBookById(Long bookId) {
        return bookRepository.findById(bookId)
                .map(book -> {
                    // 1. 리뷰 목록 가져오기 (기존 코드)
                    List<Review> reviews = reviewRepository
                            .findByBookId(bookId, Pageable.unpaged())
                            .getContent();

                    // [2] AI 요약본 가져오기 (추가된 부분)
                    // (요약이 없으면 null 반환)
                    String aiSummary = bookReviewAiRepository.findByBookId(bookId)
                            .map(BookReviewAi::getSummary)
                            .orElse(null);

                    // [3] DTO 생성 시 aiSummary 전달
                    // (BookResponse.from 메서드의 파라미터 순서에 맞춰서 넣어주세요)
                    // 만약 기존 코드가 from(book, null, reviews) 였다면,
                    // null 자리가 aiSummary 자리인지, 아니면 새로 파라미터를 추가해야 하는지 확인 필요합니다.
                    return BookResponse.fromWithReviewSummary(book, aiSummary, reviews);
                });
    }
}
