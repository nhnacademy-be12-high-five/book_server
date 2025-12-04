package com.nhnacademy.book_server.repository;

import com.nhnacademy.book_server.dto.BookResponse;
import com.nhnacademy.book_server.dto.BookSortType;
import com.nhnacademy.book_server.dto.SearchResult;

import java.util.List;

/**
 * Elasticsearch 도서 검색용 Repository 인터페이스
 */
public interface ElasticRepository {

    /**
     * ES book_index에서 키워드 검색
     *
     * @param keyword 검색어
     * @param sort    정렬 기준 (POPULAR / NEW / LOW_PRICE / HIGH_PRICE / RATING / REVIEW)
     * @param page    페이지 번호 (0부터 시작)
     * @param size    페이지당 조회 건수
     * @return 검색 결과 목록 + 전체 검색 건수(totalHits)
     */
    SearchResult<BookResponse> search(String keyword, BookSortType sort, int page, int size);

    /**
     * 여러 도서를 ES 인덱스에 저장 (reindex 용)
     */
    void saveAll(List<BookResponse> books);
}
