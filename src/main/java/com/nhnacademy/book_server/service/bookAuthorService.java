package com.nhnacademy.book_server.service;

import com.nhnacademy.book_server.entity.Author;
import com.nhnacademy.book_server.entity.Book;
import com.nhnacademy.book_server.entity.BookAuthor;
import org.springframework.stereotype.Service;

@Service
public class bookAuthorService {

    public static BookAuthor save(Book book, Author author) {

        return BookAuthor.builder()
                .book(book)
                .author(author)
                .build();
    }
}
