package com.nhnacademy.book_server.dto;

import java.util.List;

public record ReviewImageDeleteEvent(
        List<String> imageUrls
) {}
