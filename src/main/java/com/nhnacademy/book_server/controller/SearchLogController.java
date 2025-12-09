package com.nhnacademy.book_server.controller;

import com.nhnacademy.book_server.dto.response.SearchLogResponse;
import com.nhnacademy.book_server.service.search.SearchLogService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "검색 로그", description = "인기 검색어 조회 API")
@RestController
@RequestMapping("/api/search")
@RequiredArgsConstructor
public class SearchLogController {

    private final SearchLogService searchLogService;

    @Operation(summary = "인기 검색어 조회", description = "검색 로그를 기반으로 상위 인기 검색어를 조회합니다.")
    @GetMapping("/popular")
    public ResponseEntity<List<SearchLogResponse>> getPopularKeywords(
            @RequestParam(name = "limit", defaultValue = "10") int limit
    ) {
        List<SearchLogResponse> popularKeywords = searchLogService.getPopularKeywords(limit);
        return ResponseEntity.ok(popularKeywords);
    }
}
