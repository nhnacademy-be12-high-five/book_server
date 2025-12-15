package com.nhnacademy.book_server.dto;

public record ReviewCreatedEvent(
        Long memberId,
        Long bookId,
        String eventType
) {}