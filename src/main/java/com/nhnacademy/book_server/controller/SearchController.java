package com.nhnacademy.book_server.controller;

import com.nhnacademy.book_server.controller.swagger.SearchSwagger;
import com.nhnacademy.book_server.dto.BookSortType;
import com.nhnacademy.book_server.dto.BookResponse;
import com.nhnacademy.book_server.service.search.BookSearchService;
import com.nhnacademy.book_server.service.read.BookReadService;
import com.nhnacademy.book_server.service.search.ElasticService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/search")
@RequiredArgsConstructor
public class SearchController implements SearchSwagger {

    private final BookSearchService bookSearchService;
    private final BookReadService bookReadService;
    private final ElasticService elasticService;

    @Override
    @GetMapping
    public ResponseEntity<Page<BookResponse>> searchBooks(
            @RequestParam String keyword,
            @RequestParam(defaultValue = "POPULAR") BookSortType sort,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return ResponseEntity.ok(
                bookSearchService.searchBooks(keyword, sort, page, size)
        );
    }

    // 전체 도서를 ES book_index 에 다시 색인
    @PostMapping("/reindex")
    public ResponseEntity<String> reindex() {
        List<BookResponse> books = bookReadService.findAllBooks();
        elasticService.saveAll(books);   // ElasticService 에서 bulk 저장 처리

        return ResponseEntity.ok("ES 인덱싱 완료: 총 " + books.size() + "권");
    }

    
}