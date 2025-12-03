package com.nhnacademy.book_server.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

public record SearchLogResponse(

        @Schema(description = " 검색어")
        String keyword,

        @Schema(description = "검색 횟수")
        long searchCount
) {
}
