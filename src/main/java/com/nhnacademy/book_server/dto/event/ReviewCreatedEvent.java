package com.nhnacademy.book_server.dto.event;

public record ReviewCreatedEvent(
        Long memberId,
        Long bookId,
        String eventType
) {}