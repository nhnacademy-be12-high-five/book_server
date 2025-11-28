package com.nhnacademy.book_server.repository;

import com.nhnacademy.book_server.entity.Book;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BookRepository extends JpaRepository<Book, Long> {
}
