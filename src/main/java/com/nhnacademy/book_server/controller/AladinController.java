package com.nhnacademy.book_server.controller;

import com.nhnacademy.book_server.response.AladinSearchResponse;
import com.nhnacademy.book_server.service.AladinService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/aladin")
@RequiredArgsConstructor
public class AladinController {

    private final AladinService aladinService;

    // 한권 조회
    @GetMapping("/search")
    public AladinSearchResponse search(@RequestParam String query, @RequestParam String queryType) {
        return aladinService.searchBooks(query, queryType);
    }

    // 전체 조회
}
