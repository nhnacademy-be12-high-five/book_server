package com.nhnacademy.book_server.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class ReviewCreatedEvent {
    private Long memberId;
    private String eventType;
}
