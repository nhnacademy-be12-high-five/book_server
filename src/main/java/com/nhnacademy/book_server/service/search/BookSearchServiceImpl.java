package com.nhnacademy.book_server.service.search;

import com.nhnacademy.book_server.dto.BookResponse;
import com.nhnacademy.book_server.dto.BookSortType;
import com.nhnacademy.book_server.repository.ElasticRepository;
import com.nhnacademy.book_server.service.read.BookReadService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;

@Service
@RequiredArgsConstructor
public class BookSearchServiceImpl implements BookSearchService {

    // ES 검색용
    private final ElasticRepository elasticRepository;

    // 전체 조회 / 단건 조회용 (CSV/DB)
    private final BookReadService bookReadService;

    // 인기도 계산용 (검색 로그)
    private final SearchLogService searchLogService;


    //키워드 기반 도서 검색
    //실제 검색,가중치 -> es
    //정렬,페이징,검색로그 -> java
    @Override
    public Page<BookResponse> searchBooks(String keyword, BookSortType sort, int page, int size) {
        PageRequest pageRequest = PageRequest.of(page, size);

        if (keyword == null || keyword.isBlank()) {
            return Page.empty(pageRequest);
        }

        // 1) 검색 로그 기록 (키워드 단위)
        searchLogService.setSearchLog(keyword);

        // 2) ES 검색 호출 (여유 있게 page+1 만큼 조회)
        int fetchSize = (page + 1) * size;
        List<BookResponse> books = elasticRepository.search(keyword, fetchSize);

        if (books.isEmpty()) {
            return new PageImpl<>(List.of(), pageRequest, 0);
        }

        // 3) 정렬 기준 적용 (ES의 기본 relevance 순서를 바꾸고 싶을 때만)
        Comparator<BookResponse> comparator = resolveComparator(sort);
        if (comparator != null) {
            books.sort(comparator);
        }
        // sort == null 이면 ES 결과 순서를 그대로 사용

        // 4) 페이징 잘라내기
        int from = page * size;
        int to = Math.min(from + size, books.size());
        if (from >= books.size()) {
            return new PageImpl<>(List.of(), pageRequest, books.size());
        }

        List<BookResponse> content = books.subList(from, to);
        return new PageImpl<>(content, pageRequest, books.size());
    }

    //전체 도서 조회
    @Override
    public Page<BookResponse> getAllBooks(int page, int size) {
        List<BookResponse> allBooks = bookReadService.findAllBooks();

        PageRequest pageRequest = PageRequest.of(page, size);
        int from = pageRequest.getPageNumber() * pageRequest.getPageSize();
        int to = Math.min(from + pageRequest.getPageSize(), allBooks.size());

        if (from >= allBooks.size()) {
            return new PageImpl<>(List.of(), pageRequest, allBooks.size());
        }

        List<BookResponse> content = allBooks.subList(from, to);
        return new PageImpl<>(content, pageRequest, allBooks.size());
    }

    //단일 도서 조회
    @Override
    public BookResponse getBookById(Long id) {
        return bookReadService.findBookById(id)
                .orElseThrow(() -> new IllegalArgumentException("해당 ID의 도서를 찾을 수 없습니다: " + id));
    }

    //정렬
    private Comparator<BookResponse> resolveComparator(BookSortType sort) {
        if (sort == null) {
            // null 이면 ES 기본 점수 순서를 그대로 사용
            return null;
        }

        return switch (sort) {
            // 인기도: SearchLog 에 저장된 검색 횟수 기준 (내림차순)
            case POPULAR -> Comparator
                    .comparingLong((BookResponse b) -> {
                        String title = b.title();
                        if (title == null || title.isBlank()) {
                            return 0L;
                        }
                        return searchLogService.getSearchCount(title);
                    })
                    .reversed();

            // 최저가: price 오름차순
            case LOW_PRICE -> Comparator.comparing(
                    (BookResponse b) -> b.price() != null ? b.price() : Integer.MAX_VALUE
            );

            // 최고가: price 내림차순
            case HIGH_PRICE -> Comparator
                    .comparing((BookResponse b) -> b.price() != null ? b.price() : 0)
                    .reversed();

            // 신상품: 발행일 내림차순 (문자열이 yyyy-MM-dd 형식이라고 가정)
            case NEW -> Comparator
                    .comparing(
                            BookResponse::publishedDate,
                            Comparator.nullsLast(Comparator.naturalOrder())
                    )
                    .reversed();

            // 평점: 리뷰수 100건 이상 먼저, 그 안에서 avgRating 내림차순
            case RATING -> Comparator
                    .comparingLong((BookResponse b) -> {
                        Long count = b.reviewCount();
                        return (count != null && count >= 100L) ? 1L : 0L;
                    })
                    .reversed()
                    .thenComparing(
                            (BookResponse b) -> {
                                Double avg = b.avgRating();
                                return avg != null ? avg : 0.0;
                            },
                            Comparator.reverseOrder()
                    );

            // 리뷰 수: reviewCount 내림차순
            case REVIEW -> Comparator
                    .comparingLong((BookResponse b) -> {
                        Long cnt = b.reviewCount();
                        return cnt != null ? cnt : 0L;
                    })
                    .reversed();
        };
    }
}
