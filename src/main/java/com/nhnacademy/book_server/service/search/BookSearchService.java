package com.nhnacademy.book_server.service.search;

import com.nhnacademy.book_server.dto.BookResponse;
import com.nhnacademy.book_server.dto.BookSortType;
import com.nhnacademy.book_server.entity.Book;
import org.springframework.data.domain.Page;

public interface BookSearchService {

    Page<BookResponse> searchBooks(String keyword,
                                   BookSortType sortType,
                                   int page,
                                   int size);

    Page<BookResponse> searchBooksByRag(String keyword,
                                        int page,
                                        int size,
                                        BookSortType sortType);
    void indexBook(Book book);
}
