package com.nhnacademy.book_server.dto.event;

public record ReviewDeletedEvent(
        Long memberId,
        Long bookId
) { }
