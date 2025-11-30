package com.nhnacademy.book_server.service.read;

import com.nhnacademy.book_server.dto.BookResponse;

import java.util.List;
import java.util.Optional;

public interface BookReadService {

    //도서 데이터를 가져오는 서비스
    List<BookResponse> findAllBooks();
    Optional<BookResponse> findBookById(Long id);
}
