package com.nhnacademy.book_server.service;

import com.nhnacademy.book_server.entity.Book;
import com.nhnacademy.book_server.entity.Publisher;
import com.nhnacademy.book_server.parser.ParsingDto;
import com.nhnacademy.book_server.repository.publisherRepository;
import org.springframework.stereotype.Service;

@Service
public class publisherService {

    public static Publisher save(String name) {
        return Publisher.builder()
                .name(name)
                .build();
    }
}
