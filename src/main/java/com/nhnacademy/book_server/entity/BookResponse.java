package com.nhnacademy.book_server.entity;

public record BookResponse(Long id, String title, String author, Long price, String image) {
}
