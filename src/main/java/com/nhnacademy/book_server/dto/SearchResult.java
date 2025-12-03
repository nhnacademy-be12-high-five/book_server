package com.nhnacademy.book_server.dto;

import java.util.List;

// Elasticsearch 검색 결과 + 전체 건수(totalHits)를 담는 DTO

public record SearchResult<T>(
        List<T> content,
        long totalHits
) {
}
