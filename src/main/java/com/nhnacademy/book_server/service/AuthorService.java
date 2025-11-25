package com.nhnacademy.book_server.service;

import com.nhnacademy.book_server.entity.Author;
import org.springframework.stereotype.Service;

@Service
public class AuthorService {

    public Author save(String name) {
        return Author.builder()
                .name(name)
                .build();
    }
}
