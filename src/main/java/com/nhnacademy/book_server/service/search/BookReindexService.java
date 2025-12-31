package com.nhnacademy.book_server.service.search;

import com.nhnacademy.book_server.dto.BookResponse;
import com.nhnacademy.book_server.entity.Book;
import com.nhnacademy.book_server.entity.Review;
import com.nhnacademy.book_server.repository.BookRepository;
import com.nhnacademy.book_server.repository.review.ReviewRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class BookReindexService {

    private static final int PAGE_SIZE = 1000; // 한 번에 처리할 도서 수(환경에 맞게 조정)

    private final BookRepository bookRepository;
    private final ReviewRepository reviewRepository;
    private final ElasticService elasticService;

    /**
     * 전체 도서를 페이지 단위로 읽어서 ES(book_index)에 재색인
     * - BookReadServiceImpl 을 사용하지 않고,
     *   Book / Review 를 페이지 단위로 직접 조회하여 N+1을 완화한다.
     *
     * @return 인덱싱한 도서 수
     */
    @Transactional(readOnly = true)
    public long reindexAll() {

        long totalBooks = bookRepository.count();
        if (totalBooks == 0) {
            log.warn("일반 검색 인덱스 재색인: 도서가 0권입니다.");
            return 0L;
        }
        //등록 시에 문제가 생길거 고려

        int totalPages = (int) Math.ceil((double) totalBooks / PAGE_SIZE);
        log.info("일반 검색 인덱스 재색인 시작 - 총 도서 수: {}, 페이지 수: {}", totalBooks, totalPages);

        for (int page = 0; page < totalPages; page++) {
            Page<Book> bookPage = bookRepository.findAll(PageRequest.of(page, PAGE_SIZE));
            List<Book> books = bookPage.getContent();

            if (books.isEmpty()) {
                continue;
            }

            // 현재 페이지에 포함된 bookId 목록
            List<Long> bookIds = books.stream()
                    .map(Book::getId)
                    .toList();

            // 해당 페이지의 모든 리뷰를 한 번에 조회 (N+1 방지)
            List<Review> reviews = reviewRepository.findByBookIdIn(bookIds);
            //1번 도서: 리뷰10개 식의 dto 생성해서 붙이기 (리뷰 다 가져올 필요없음)
            //리뷰 인덱싱하는 주기 생각해야함 (업데이트)

            // bookId -> 리뷰 리스트 매핑
            Map<Long, List<Review>> reviewMap = reviews.stream()
                    .collect(Collectors.groupingBy(review -> review.getBook().getId()));

            // Book + 리뷰를 이용해 BookResponse 생성
            List<BookResponse> docs = books.stream()
                    .map(book -> {
                        List<Review> bookReviews =
                                reviewMap.getOrDefault(book.getId(), Collections.emptyList());
                        // 카테고리는 인덱싱에서는 사용하지 않으므로 null
                        return BookResponse.from(book, null, bookReviews);
                    })
                    .toList();

            // ES 인덱싱 (bulk)
            elasticService.saveAll(docs);

            log.info("일반 검색 인덱스 재색인 진행 상황: {}/{} 페이지 처리 완료 ({}권)",
                    page + 1, totalPages, books.size());
        }

        log.info("일반 검색 인덱스 재색인 완료 - 총 {}권 인덱싱", totalBooks);
        return totalBooks;
    }
}
