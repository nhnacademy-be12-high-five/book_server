package com.nhnacademy.book_server.service.search;

import com.nhnacademy.book_server.config.RagSearchConfig;
import com.nhnacademy.book_server.dto.BookResponse;
import com.nhnacademy.book_server.dto.BookSortType;
import com.nhnacademy.book_server.dto.SearchResult;
import com.nhnacademy.book_server.repository.ElasticRepository;
import com.nhnacademy.book_server.service.read.BookReadService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class BookSearchServiceImpl implements BookSearchService {

    private final ElasticRepository elasticRepository;
    private final BookReadService bookReadService;
    private final SearchLogService searchLogService;
    private final RagSearchable ragSearchable;

    //키워드 기반 도서 검색
    //검색, 동의어, 가중치, 정렬 -> ES(ElasticService)
    //검색로그, Page 객체 변환 -> Java (여기)
    @Override
    public Page<BookResponse> searchBooks(String keyword, BookSortType sort, int page, int size) {
        Pageable pageable = PageRequest.of(page, size);

        // 검색어 없으면 빈 페이지
        if (keyword == null || keyword.isBlank()) {
            return Page.empty(pageable);
        }

        // 검색 로그 기록 (키워드 단위)
        searchLogService.setSearchLog(keyword);

        // ES 검색 호출 (결과 + totalHits 함께 반환)
        SearchResult<BookResponse> result =
                elasticRepository.search(keyword, sort, page, size);

        List<BookResponse> content = result.content();
        long total = result.totalHits();

        return new PageImpl<>(content, pageable, total);
    }

    //전체 도서 조회
    @Override
    public Page<BookResponse> getAllBooks(int page, int size) {
        List<BookResponse> allBooks = bookReadService.findAllBooks();

        Pageable pageable = PageRequest.of(page, size);
        int from = page * size;
        int to = Math.min(from + size, allBooks.size());

        if (from >= allBooks.size()) {
            return new PageImpl<>(List.of(), pageable, allBooks.size());
        }

        List<BookResponse> content = allBooks.subList(from, to);
        return new PageImpl<>(content, pageable, allBooks.size());
    }

    //단일 도서 조회
    @Override
    public BookResponse getBookById(Long id) {
        return bookReadService.findBookById(id)
                .orElseThrow(() -> new IllegalArgumentException("해당 ID의 도서를 찾을 수 없습니다: " + id));
    }

    //RAG 검색 메서드
    @Override
    public Page<BookResponse> searchBooksByRag(String keyword, int page, int size) {
        Pageable pageable = PageRequest.of(page, size);

        if (keyword == null || keyword.isBlank()) {
            return Page.empty(pageable);
        }

        searchLogService.setSearchLog(keyword);

        SearchResult<BookResponse> result =
                ragSearchable.searchByRag(keyword, page, size);

        return new PageImpl<>(result.content(), pageable, result.totalHits());
    }

}
