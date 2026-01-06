package com.nhnacademy.book_server.repository;

import com.nhnacademy.book_server.dto.response.BookDocument;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;

/**
 * Elasticsearch 도서 검색용 Repository 인터페이스
 */
public interface ElasticRepository extends ElasticsearchRepository<BookDocument, Long> {

    /**
     * ES book_index에서 키워드 검색
     *
     * @param keyword 검색어
     * @param sort    정렬 기준 (POPULAR / NEW / LOW_PRICE / HIGH_PRICE / RATING / REVIEW)
     * @param page    페이지 번호 (0부터 시작)
     * @param size    페이지당 조회 건수
     * @return 검색 결과 목록 + 전체 검색 건수(totalHits)
     */
}
