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

import java.util.ArrayList;
import java.util.LinkedHashMap;
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
    public Page<BookResponse> searchBooks(String keyword,
                                          BookSortType sortType,
                                          int page,
                                          int size) {

        Pageable pageable = PageRequest.of(page, size);

        // 키워드 없으면 빈 페이지 리턴
        if (keyword == null || keyword.isBlank()) {
            return Page.empty(pageable);
        }

        // ES 일반 검색 호출
        SearchResult<BookResponse> result =
                elasticRepository.search(keyword, sortType, page, size);

        return new PageImpl<>(result.content(), pageable, result.totalHits());
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


    @Override
    public Page<BookResponse> searchBooksByRag(String keyword, int page, int size, BookSortType sortType) {
        Pageable pageable = PageRequest.of(page, size);

        if (keyword == null || keyword.isBlank()) {
            return Page.empty(pageable);
        }

        // 1. 검색 로그 기록
        searchLogService.setSearchLog(keyword);

        // 2. 키워드 검색 (POPULAR 기준, 병합용으로 넉넉하게)
        int baseSize = 100;
        SearchResult<BookResponse> keywordResult =
                elasticRepository.search(keyword, BookSortType.POPULAR, 0, baseSize);

        // 3. RAG 벡터 검색
        SearchResult<BookResponse> ragResult =
                ragSearchable.searchByRag(keyword, 0, baseSize);

        // 4. 두 결과 병합 (키워드 우선, RAG 추가)
        LinkedHashMap<Long, BookResponse> merged = new LinkedHashMap<>();
        for (BookResponse book : keywordResult.content()) {
            merged.put(book.id(), book);
        }
        for (BookResponse book : ragResult.content()) {
            merged.putIfAbsent(book.id(), book);
        }

        List<BookResponse> mergedList = new ArrayList<>(merged.values());

        // ★★ 4-3. Fallback: 둘 다 비어 있으면 그냥 일반 검색 결과라도 리턴 ★★
        if (mergedList.isEmpty()) {
            // 여기서는 실제 페이지/사이즈로 다시 검색
            SearchResult<BookResponse> fallback =
                    elasticRepository.search(keyword, BookSortType.POPULAR, page, size);

            return new PageImpl<>(
                    fallback.content(),
                    pageable,
                    fallback.totalHits()
            );
        }

        // 5. 병합 리스트에서 페이징
        long total = mergedList.size();
        int from = page * size;
        int to = Math.min(from + size, mergedList.size());

        if (from >= mergedList.size()) {
            return new PageImpl<>(List.of(), pageable, total);
        }

        List<BookResponse> pageContent = mergedList.subList(from, to);
        return new PageImpl<>(pageContent, pageable, total);
    }


}
