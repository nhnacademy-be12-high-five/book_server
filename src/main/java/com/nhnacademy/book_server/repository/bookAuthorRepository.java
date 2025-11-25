package com.nhnacademy.book_server.repository;

import com.nhnacademy.book_server.entity.BookAuthor;
import org.springframework.data.jpa.repository.JpaRepository;

public interface bookAuthorRepository extends JpaRepository<BookAuthor,BookAuthor.Pk> {
}
