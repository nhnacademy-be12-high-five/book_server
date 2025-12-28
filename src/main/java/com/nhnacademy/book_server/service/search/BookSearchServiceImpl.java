package com.nhnacademy.book_server.service.search;

import com.nhnacademy.book_server.dto.BookResponse;
import com.nhnacademy.book_server.dto.BookSortType;
import com.nhnacademy.book_server.dto.SearchResult;
import com.nhnacademy.book_server.dto.response.BookDocument;
import com.nhnacademy.book_server.entity.Book;
import com.nhnacademy.book_server.repository.ElasticRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;

@Service
@RequiredArgsConstructor
public class BookSearchServiceImpl implements BookSearchService {

    private final ElasticRepository elasticRepository;
    private final ElasticService elasticService;
    private final SearchLogService searchLogService;
    private final RagSearchable ragSearchable;

    // -------------------- 일반 검색 --------------------
    @Override
    public Page<BookResponse> searchBooks(String keyword,
                                          BookSortType sortType,
                                          int page,
                                          int size) {

        Pageable pageable = PageRequest.of(page, size);

        if (keyword == null || keyword.isBlank()) {
            return Page.empty(pageable);
        }

        SearchResult<BookResponse> result =
                elasticService.search(keyword, sortType, page, size);

        return new PageImpl<>(result.content(), pageable, result.totalHits());
    }


    // -------------------- RAG 하이브리드 검색 --------------------
    @Override
    public Page<BookResponse> searchBooksByRag(String keyword,
                                               int page,
                                               int size,
                                               BookSortType sortType) {

        Pageable pageable = PageRequest.of(page, size);

        if (keyword == null || keyword.isBlank()) {
            return Page.empty(pageable);
        }

        // 1. 검색 로그
        searchLogService.setSearchLog(keyword);

        int baseSize = 100; // 병합용 후보 수

        // 2. 키워드 검색 (POPULAR 기준, 병합용)
        SearchResult<BookResponse> keywordResult =
                elasticService.search(keyword, BookSortType.POPULAR, 0, baseSize);

        // 3. RAG 벡터 검색
        SearchResult<BookResponse> ragResult =
                ragSearchable.searchByRag(keyword, 0, baseSize);

        // 4. 결과 병합 (키워드 우선, RAG 추가)
        LinkedHashMap<Long, BookResponse> merged = new LinkedHashMap<>();
        for (BookResponse book : keywordResult.content()) {
            merged.put(book.bookId(), book);
        }
        for (BookResponse book : ragResult.content()) {
            merged.putIfAbsent(book.bookId(), book);
        }

        List<BookResponse> mergedList = new ArrayList<>(merged.values());

        // 4-1. 키워드/RAG 둘 다 비어 있으면 → 그냥 일반 검색 결과라도 리턴
        if (mergedList.isEmpty()) {
            SearchResult<BookResponse> fallback =
                    elasticService.search(keyword, sortType, page, size);

            return new PageImpl<>(
                    fallback.content(),
                    pageable,
                    fallback.totalHits()
            );
        }

        // 5. 정렬 옵션 적용 (자바 레벨)
        if (sortType != null) {
            Comparator<BookResponse> comparator = null;
            switch (sortType) {
                case LOW_PRICE -> comparator = Comparator
                        .comparing(BookResponse::price, Comparator.nullsLast(Integer::compareTo));
                case HIGH_PRICE -> comparator = Comparator
                        .comparing(BookResponse::price, Comparator.nullsLast(Integer::compareTo))
                        .reversed();
                case RATING -> comparator = Comparator
                        .comparing(BookResponse::avgRating, Comparator.nullsLast(Double::compareTo))
                        .reversed();
                case REVIEW -> comparator = Comparator
                        .comparing(BookResponse::reviewCount, Comparator.nullsLast(Long::compareTo))
                        .reversed();
                case NEW -> comparator = (b1, b2) -> {
                    String d1 = b1.publishedDate();
                    String d2 = b2.publishedDate();
                    if (d1 == null && d2 == null) return 0;
                    if (d1 == null) return 1;
                    if (d2 == null) return -1;
                    // 최신순 → 내림차순
                    return d2.compareTo(d1);
                };
                case POPULAR -> {
                    // 인기순 점수 필드는 따로 없으므로, 현재 병합 순서(키워드 우선)를 그대로 사용
                }
            }

            if (comparator != null) {
                mergedList.sort(comparator);
            }
        }

        // 6. 병합 리스트에서 페이징
        long total = mergedList.size();
        int from = page * size;
        int to = Math.min(from + size, mergedList.size());

        if (from >= mergedList.size()) {
            return new PageImpl<>(List.of(), pageable, total);
        }

        List<BookResponse> pageContent = mergedList.subList(from, to);
        return new PageImpl<>(pageContent, pageable, total);
    }

    @Override
    public void indexBook(Book book) {
        BookResponse bookResponse = BookResponse.from(book);
        BookDocument bookDocument = BookDocument.from(bookResponse);
        elasticRepository.save(bookDocument);
    }
}
