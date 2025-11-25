package com.nhnacademy.book_server.repository;

import com.nhnacademy.book_server.entity.Book;
import com.nhnacademy.book_server.parser.ParsingDto;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Set;

public interface BookRepository extends JpaRepository<Book, Long> {

    List<Book> findAllByIsbnIn(Set<String> allIsbns);

    List<Book> findAllByBookId(Long bookId);
}