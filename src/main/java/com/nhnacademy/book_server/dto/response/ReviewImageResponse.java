package com.nhnacademy.book_server.dto.response;

import java.io.Serializable;

public record ReviewImageResponse(
        Long imageId,
        String imageUrl
) implements Serializable {
}