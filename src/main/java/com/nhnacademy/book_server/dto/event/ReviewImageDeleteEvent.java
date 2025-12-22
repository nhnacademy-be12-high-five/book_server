package com.nhnacademy.book_server.dto.event;

import java.util.List;

public record ReviewImageDeleteEvent(
        List<String> imageUrls
) {}
