package com.nhnacademy.book_server.controller;

import com.nhnacademy.book_server.controller.swagger.SearchSwagger;
import com.nhnacademy.book_server.dto.BookSortType;
import com.nhnacademy.book_server.dto.BookResponse;
import com.nhnacademy.book_server.service.search.BookSearchService;
import com.nhnacademy.book_server.service.read.BookReadService;
import com.nhnacademy.book_server.service.search.ElasticService;
import com.nhnacademy.book_server.service.search.RagSearchable;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/search")
@RequiredArgsConstructor
public class SearchController implements SearchSwagger {

    private final BookSearchService bookSearchService;
    private final BookReadService bookReadService;
    private final ElasticService elasticService;
    private final RagSearchable ragSearchable;

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
        try {
            // 1. DB에서 전체 도서 조회
            List<BookResponse> books = bookReadService.findAllBooks();

            // 2. 기본 검색 인덱스(book_index) 재색인
            elasticService.saveAll(books);

            // 3. RAG용 임베딩 인덱스(book_embedding_index) 재색인
            ragSearchable.reindexBooks();

            return ResponseEntity.ok("ES 인덱싱 완료: 총 " + books.size() + "권");

        } catch (Exception exception) {
            log.error("reindex 실행 중 오류 발생", exception);
            return ResponseEntity
                    .status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("reindex 중 서버 오류: " + exception.getMessage());
        }
    }



    @GetMapping("/rag-search")
    public ResponseEntity<Page<BookResponse>> searchBooksByRag(
            @RequestParam String keyword,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        Page<BookResponse> result = bookSearchService.searchBooksByRag(keyword, page, size);
        return ResponseEntity.ok(result);
    }

}


