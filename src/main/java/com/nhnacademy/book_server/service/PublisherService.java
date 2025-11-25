package com.nhnacademy.book_server.service;

import com.nhnacademy.book_server.entity.Publisher;
import org.springframework.stereotype.Service;

@Service
public class PublisherService {

    public static Publisher save(String name) {
        return Publisher.builder()
                .name(name)
                .build();
    }
}
