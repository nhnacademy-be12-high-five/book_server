package com.nhnacademy.book_server.controller;

import com.nhnacademy.book_server.controller.swagger.UserBookSwagger;
import com.nhnacademy.book_server.dto.BookResponse;
import com.nhnacademy.book_server.dto.response.GetBookResponse;
import com.nhnacademy.book_server.service.BookService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api")
@Tag(name = "도서 API - 사용자", description = "사용자를 위한 도서 API 입니다.")
@RequiredArgsConstructor
public class UserBookController implements UserBookSwagger {

    private final BookService bookService;
    @Autowired
    private org.springframework.data.redis.core.StringRedisTemplate redisTemplate;

    // 도서 전체 조회 (GET /api/books)
    @Override
    @GetMapping("/books")
    public ResponseEntity<Page<BookResponse>> getAllBooks(@RequestHeader(value = "X-USER-ID", required = false) Long memberId,
                                                          @PageableDefault(size = 10) Pageable pageable) {
        // [수정 1] getContent() 대신 Page 객체 그대로 반환
        // 프론트엔드에서 totalElements, totalPages를 알 수 있게 됩니다.
        Page<BookResponse> bookPage = bookService.findAllBooks(pageable);
        return ResponseEntity.ok(bookPage);
    }

    // todo page 전체 개수 (response) Page 수정

    // 도서 한 권 상세 조회 (GET /api/books/{bookId})
    @Override
    @GetMapping("/books/{id}")
    public ResponseEntity<BookResponse> getBookById(@PathVariable("id") Long bookId) {
        // [수정 2] Service가 이미 DTO를 반환하므로 .map() 제거
        // 앞서 BookService.findBookById를 BookResponse 반환으로 수정했기 때문입니다.
        try {
            BookResponse response = bookService.findBookById(bookId);
            return ResponseEntity.ok(response);
        } catch (RuntimeException e) {
            return ResponseEntity.notFound().build();
        }
    }

    // todo api 버전

    // todo api 수정
    @PostMapping("/books/bulk")
    public ResponseEntity<List<GetBookResponse>> getBooksBulk(@RequestBody List<Long> bookIds) {
        List<GetBookResponse> response = bookService.getBooksBulk(bookIds);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/books/new")
    public ResponseEntity<List<BookResponse>> getNewBooks(@RequestParam(defaultValue = "5") int size) {
        List<BookResponse> books = bookService.getNewBooks();
        return ResponseEntity.ok(books);
    }

    @GetMapping("/books/popular")
    public ResponseEntity<List<BookResponse>> getWeeklyPopular(@RequestParam(defaultValue = "5") int size){
        List<BookResponse> books = bookService.getWeeklyPopularBooks(size);
//        System.out.println("컨트롤러 호출됨! 찾은 책 개수: " + books.size());
        return ResponseEntity.ok(books);
    }

    // 판매량 반영 API
    @PostMapping("/books/{bookId}/best-seller")
    public ResponseEntity<Void> updateBestSellerScore(@PathVariable("bookId") Long bookId,
                                                      @RequestBody Integer quantity) {
        bookService.incrementBestSellerScore(bookId, quantity);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/books/best-seller")
    public ResponseEntity<List<BookResponse>> getBestSeller(@RequestParam(defaultValue = "5") int size){
        List<BookResponse> BestSellers=bookService.getBestSeller(size);
        return ResponseEntity.ok(BestSellers);
    }

    @PostMapping("/books/{bookId}/category/{categoryId}")
    public ResponseEntity<Void> mapCategory(@PathVariable("bookId") Long bookId, @PathVariable("categoryId") Integer categoryId) {
        bookService.saveBookWithCategory(bookId,categoryId);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/books/manage/setup-categories")
    public ResponseEntity<String> setupCategories() {
        System.out.println("set");
        bookService.migrateCategories();
        return ResponseEntity.ok("2000권의 도서 카테고리 매핑이 완료되었습니다.");
    }

    // UserBookController.java 안에 추가하세요


    @GetMapping("/books/safe-cleanup")
    public ResponseEntity<String> safeCleanup() {
        StringBuilder result = new StringBuilder();

        // 1. [가장 중요] 에러를 유발하는 '신간 목록' 캐시 하나만 딱 지웁니다.
        Boolean newBooksDeleted = redisTemplate.delete("newBooks::default");
        if (Boolean.TRUE.equals(newBooksDeleted)) {
            result.append("✅ 'newBooks::default' 삭제 성공 (메인 화면 에러 해결)<br>");
        } else {
            result.append("⚠️ 'newBooks::default'가 없거나 이미 지워짐<br>");
        }

        // 2. '책 상세 정보' 캐시들만 찾아서 지웁니다. (다른 데이터 안 건드림)
        // "bookDetail::"로 시작하는 키만 찾습니다.

        java.util.Set<String> detailKeys = redisTemplate.keys("bookDetail::*");

        if (detailKeys != null && !detailKeys.isEmpty()) {
            redisTemplate.delete(detailKeys);
            result.append("✅ 'bookDetail' 관련 데이터 " + detailKeys.size() + "개 삭제 성공 (상세 페이지 에러 해결)");
        } else {
            result.append("⚠️ 'bookDetail' 관련 캐시가 없음");
        }

        return ResponseEntity.ok(result.toString());
    }

    @GetMapping("/books/category")
    public String fixCategories() {
        bookService.migrateCategories();
        return "카테고리 연결 데이터 복구 완료!";
    }
}