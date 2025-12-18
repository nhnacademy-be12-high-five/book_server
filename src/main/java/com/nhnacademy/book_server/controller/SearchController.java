package com.nhnacademy.book_server.controller;

import com.nhnacademy.book_server.controller.swagger.SearchSwagger;
import com.nhnacademy.book_server.dto.BookResponse;
import com.nhnacademy.book_server.dto.BookSortType;
import com.nhnacademy.book_server.service.search.*;
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
    private final GeminiTextClientService geminiTextClientService;

    /**
     *  로컬/시연 환경에서 RAG reindex 폭주 방지 토글
     * - 기본값 false
     * - application-local.yml에서 rag.reindex.enabled=true 로 켜면 동작
     */
    @Value("${rag.reindex.enabled:false}")
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
        //  호출 폭주/반복 여부를 잡기 위한 최소 로그
        log.info("RAG-ANSWER 호출 keyword=[{}]", keyword);

        // 1. RAG 검색으로 상위 5권 가져오기
        Page<BookResponse> page =
                bookSearchService.searchBooksByRag(keyword, 0, 5, BookSortType.POPULAR);

        List<BookResponse> books = page.getContent();

        // 2. 후보가 없으면 기본 메시지
        if (books.isEmpty()) {
            String message = "현재 '" + keyword + "' 와(과) 관련된 도서를 찾지 못했습니다. "
                    + "키워드를 조금 더 구체적으로 입력해 보시겠어요?";
            return ResponseEntity.ok(message);
        }

        // 3. Gemini에 줄 컨텍스트 구성
        StringBuilder ctx = new StringBuilder();
        for (int i = 0; i < books.size(); i++) {
            BookResponse b = books.get(i);
            ctx.append("[%d] %s (%s)\n내용: %s\n\n".formatted(
                    i + 1,
                    b.title(),
                    b.author(),
                    b.content()
            ));
        }

        String prompt = """
        사용자 검색어: "%s"
                
        검색어: "%s"
                
        아래 도서가 이 검색어와 얼마나 관련 있는지 판단해 주세요.
                
        도서 정보:
        - 제목: %s
        - 저자: %s
        - 출판사: %s
        - 간단 설명: %s
                
        다음 형식으로만 한국어로 작성하세요.
        1) 관련도: %% 숫자 하나 (0~100)
        2) 추천 이유: 두 문장
                
        ※ 줄거리 요약, 작품 해석, 감상 금지
        """.formatted(keyword, ctx);
        // 4. Gemini 호출 (429/403 발생해도 서비스는 정상 유지되도록 메시지 반환)
        String answer = geminiTextClientService.generateAnswer(prompt);
        return ResponseEntity.ok(answer);
    }
}
