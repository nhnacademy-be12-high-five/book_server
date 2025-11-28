package com.nhnacademy.book_server.repository;

import com.nhnacademy.book_server.dto.BookResponse;

import java.util.List;

public interface ElasticRepository {
    List<BookResponse> search(String keyword, int size);
}
