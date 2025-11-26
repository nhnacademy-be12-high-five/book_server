package com.nhnacademy.book_server.service;

import com.nhnacademy.book_server.dto.BookSortType;
import com.nhnacademy.book_server.dto.BookResponse;
import org.springframework.data.domain.Page;

public interface BookSearchService {
    //도서 데이터 -> 검색수행 서비스

    Page<BookResponse> searchBooks(String keyword, BookSortType sort, int page, int size);

    Page<BookResponse> getAllBooks(int page, int size);

    BookResponse getBookById(Long id);
}
