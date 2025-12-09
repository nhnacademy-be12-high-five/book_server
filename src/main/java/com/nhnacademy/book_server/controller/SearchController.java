package com.nhnacademy.book_server.controller;

import com.nhnacademy.book_server.controller.swagger.SearchSwagger;
import com.nhnacademy.book_server.dto.BookResponse;
import com.nhnacademy.book_server.dto.BookSortType;
import com.nhnacademy.book_server.service.read.BookReadService;
import com.nhnacademy.book_server.service.search.BookSearchService;
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

    /**
     * 일반 검색
     * GET /api/search?keyword=유아&sort=POPULAR&page=0&size=20
     */
    @Override
    @GetMapping
    public ResponseEntity<Page<BookResponse>> searchBooks(
            @RequestParam String keyword,
            @RequestParam(defaultValue = "POPULAR") BookSortType sort,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        log.info("북서버 일반 검색 keyword=[{}], sort=[{}], page={}, size={}",
                keyword, sort, page, size);

        Page<BookResponse> result =
                bookSearchService.searchBooks(keyword, sort, page, size);

        return ResponseEntity.ok(result);
    }

    /**
     * 전체 도서를 ES 에 다시 색인 (book_index + book_embedding_index)
     * POST /api/search/reindex
     */
    @PostMapping("/reindex")
    public ResponseEntity<String> reindex() {
        try {
            // 1. DB 전체 도서 조회
            List<BookResponse> books = bookReadService.findAllBooks();

            // 2. 키워드 검색 인덱스 재색인
            elasticService.saveAll(books);

            // 3. RAG용 임베딩 인덱스 재색인
            ragSearchable.reindexBooks();

            return ResponseEntity.ok("ES 인덱싱 완료: 총 " + books.size() + "권");

        } catch (Exception exception) {
            log.error("reindex 실행 중 오류 발생", exception);
            return ResponseEntity
                    .status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("reindex 중 서버 오류: " + exception.getMessage());
        }
    }

    /**
     * RAG 하이브리드 검색 + 정렬
     * GET /api/search/rag-search?keyword=유아&sort=REVIEW&page=0&size=20
     */
    @GetMapping("/rag-search")
    public ResponseEntity<Page<BookResponse>> searchBooksByRag(
            @RequestParam String keyword,
            @RequestParam(defaultValue = "POPULAR") BookSortType sort,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        log.info("북서버 RAG 검색 keyword=[{}], sort=[{}], page={}, size={}",
                keyword, sort, page, size);

        Page<BookResponse> result =
                bookSearchService.searchBooksByRag(keyword, page, size, sort);

        return ResponseEntity.ok(result);
    }

    /**
     * RAG 기반 AI 요약/추천 문장
     * GET /api/search/rag-answer?keyword=유아
     */
    @GetMapping("/rag-answer")
    public ResponseEntity<String> getRagAnswer(@RequestParam String keyword) {

        // AI 요약용: POPULAR 고정, 상위 5권만 사용
        Page<BookResponse> page =
                bookSearchService.searchBooksByRag(keyword, 0, 5, BookSortType.POPULAR);

        List<BookResponse> books = page.getContent();
        String message;

        if (books.isEmpty()) {
            message = "현재 '" + keyword + "' 와(과) 관련된 도서를 찾지 못했습니다. "
                    + "키워드를 조금 더 구체적으로 입력해 보시겠어요?";
        } else {
            StringBuilder sb = new StringBuilder();
            sb.append("“").append(keyword).append("” 키워드와 관련해서는 ");

            int limit = Math.min(3, books.size());
            for (int i = 0; i < limit; i++) {
                BookResponse b = books.get(i);
                if (i > 0) {
                    sb.append(i == limit - 1 ? " 그리고 " : ", ");
                }
                sb.append("「").append(b.title()).append("」");
            }
            sb.append(" 등 ").append(page.getTotalElements())
                    .append("권의 도서가 검색되었습니다. ");

            sb.append("유사한 주제의 도서를 더 보고 싶으시면 "
                    + "좌측 정렬 옵션(인기순, 신간순, 가격순 등)을 함께 활용해 보세요.");

            message = sb.toString();
        }

        return ResponseEntity.ok(message);
    }
}
