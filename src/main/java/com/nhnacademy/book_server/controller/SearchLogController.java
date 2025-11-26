package com.nhnacademy.book_server.controller;

import com.nhnacademy.book_server.service.SearchLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Service
@RequiredArgsConstructor
@RequestMapping("/api/search-logs")
public class SearchLogController {

    private final SearchLogService searchLogService;

    //검색실행시 호출 -> 검색횟수 +1 (도서조회 끝난후 같이 호출)
    @PostMapping
    public ResponseEntity<Void> addSearchLog(@RequestParam String keyword){
        searchLogService.setSearchLog(keyword);
        return ResponseEntity.ok().build();
    }

    //특정 키워드 검색횟수 조회 -> 인기도 정렬시 활용
    @GetMapping("/count")
    public ResponseEntity<Long> getSearchCount(@RequestParam String keyword){
        long count = searchLogService.getSearchCount(keyword);
        return ResponseEntity.ok(count);
    }




}
