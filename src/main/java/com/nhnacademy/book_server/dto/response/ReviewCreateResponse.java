package com.nhnacademy.book_server.dto.response;

public record ReviewCreateResponse(Long reviewId, int rating, String content) {}
