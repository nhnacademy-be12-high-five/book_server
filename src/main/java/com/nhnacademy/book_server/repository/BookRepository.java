package com.nhnacademy.book_server.repository;

import com.nhnacademy.book_server.entity.book;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BookRepository extends JpaRepository<book, Integer> {
}
