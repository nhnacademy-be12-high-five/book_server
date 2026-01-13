package com.nhnacademy.book_server.controller;

import com.nhnacademy.book_server.controller.swagger.SearchSwagger;
import com.nhnacademy.book_server.dto.BookResponse;
import com.nhnacademy.book_server.dto.BookSortType;
import com.nhnacademy.book_server.dto.SearchResult;
import com.nhnacademy.book_server.service.search.BookReindexService;
import com.nhnacademy.book_server.service.search.BookSearchService;
import com.nhnacademy.book_server.service.search.RagAnswerService;
import com.nhnacademy.book_server.service.search.RagSearchable;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
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
    private final BookReindexService bookReindexService;
    private final RagSearchable ragSearchable;
    private final RagAnswerService ragAnswerService;

    /**
     * 로컬/시연 환경에서 RAG reindex 폭주 방지 토글
     * - 기본값 false
     * - application-local.yml에서 rag.reindex.enabled=true 로 켜면 동작
     */
    @Value("${rag.reindex.enabled:true}")
    private boolean ragReindexEnabled;

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
     * 전체 도서를 ES 에 다시 색인 (high-five + emb-high-five)
     * POST /api/search/reindex
     */

    @PostMapping("/reindex")
    public ResponseEntity<String> reindex() {
        try {
            long total = bookReindexService.reindexAll();
            return ResponseEntity.ok("일반 검색 인덱싱 완료: 총 " + total + "권");
        } catch (Exception exception) {
            log.error("일반 검색 reindex 실행 중 오류 발생", exception);
            return ResponseEntity
                    .status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("일반 검색 reindex 중 서버 오류: " + exception.getMessage());
        }
    }

    /**
     * RAG용 임베딩 인덱스(emb-high-five) 재색인
     * → 비용이 크므로 일반 인덱스와 분리
     * POST /api/search/reindex-rag
     */
    @PostMapping("/reindex-rag")
    public ResponseEntity<String> reindexRag() {
        //  기본 OFF 가드
        if (!ragReindexEnabled) {
            return ResponseEntity
                    .status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body("로컬 환경에서는 RAG reindex가 비활성화되어 있습니다. (rag.reindex.enabled=false)");
        }

        try {
            log.info("RAG reindex 실행 요청 수신");
            ragSearchable.reindexBooks();
            return ResponseEntity.ok("RAG 임베딩 인덱싱 작업을 실행했습니다.");
        } catch (Exception exception) {
            log.error("RAG reindex 실행 중 오류 발생", exception);
            return ResponseEntity
                    .status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("RAG reindex 중 서버 오류: " + exception.getMessage());
        }
    }

    /**
     * RAG 하이브리드 검색 + 정렬
     * GET /api/search/rag-search?keyword=유아&sort=REVIEW&page=0&size=20
     */
    @GetMapping("/rag-search")
    public ResponseEntity<List<BookResponse>> ragSearch(@RequestParam String keyword) {
        log.info("RAG 검색 요청 (Top 10): keyword=[{}]", keyword);

        SearchResult<BookResponse> result = ragSearchable.searchByRag(keyword, 0, 10);

        return ResponseEntity.ok(result.content());
    }

    /**
     * RAG 기반 AI 요약/추천 문장
     * GET /api/search/rag-answer?keyword=유아
     */
    @GetMapping("/rag-answer")
    public ResponseEntity<String> ragAnswer(@RequestParam String keyword) {
        log.info("RAG Answer 요청: keyword=[{}]", keyword);
        // 서비스가 (검색 -> 재순위화 -> 요약) 모든 과정을 처리하고 결과만 줍니다.
        String answer = ragAnswerService.answer(keyword);
        return ResponseEntity.ok(answer);
    }

    /**
     * 장바구니 기반 AI 추천 (추천 사유 생성 포함)
     * GET /api/search/recommendations?keyword=책제목1,책제목2
     */
    @GetMapping("/recommendations")
    public ResponseEntity<List<BookResponse>> getRecommendations(@RequestParam String keyword) {
        log.info("AI 추천 도서 요청 (Reasoning 포함): keyword=[{}]", keyword);
        List<BookResponse> recommendations = ragAnswerService.getRecommendations(keyword);
        return ResponseEntity.ok(recommendations);
    }
}
