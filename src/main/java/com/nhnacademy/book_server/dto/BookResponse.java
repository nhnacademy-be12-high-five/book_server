package com.nhnacademy.book_server.entity;


import java.time.LocalDate;

public record bookResponse(Long id, String title,
                           String author, Long price, String image) {
}
