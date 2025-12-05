package com.nhnacademy.book_server.service.search;

import com.nhnacademy.book_server.dto.BookResponse;
import com.nhnacademy.book_server.dto.SearchResult;

public interface RagSearchable {

    // 전체 도서 -> rag인덱스에 재색인
    void reindexBooks();

    //RAG(임베딩 + 벡터) 기반 도서 검색
    SearchResult<BookResponse> searchByRag(String keyword, int page, int size);
}
