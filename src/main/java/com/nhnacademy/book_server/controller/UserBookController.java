package com.nhnacademy.book_server.controller;

import com.nhnacademy.book_server.controller.swagger.UserBookSwagger;
import com.nhnacademy.book_server.dto.BookResponse;
import com.nhnacademy.book_server.dto.response.GetBookResponse;
import com.nhnacademy.book_server.service.BookService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/books") // 모든 도서 관련 API의 기본 경로를 지정
@Tag(name = "도서 API - 사용자", description = "사용자를 위한 도서 API 입니다.")
@RequiredArgsConstructor
public class UserBookController implements UserBookSwagger {

    private final BookService bookService;

    // 도서 전체 조회 (GET /api/books)
    @Override
    @GetMapping
    public ResponseEntity<List<BookResponse>> getAllBooks(@RequestHeader("X-USER-ID") Long memberId,
                                                          @PageableDefault(size = 10) Pageable pageable) {
        // 책을 한번에 로드 하기 위한 pagenation 추가
        Page<BookResponse> bookPage=bookService.findAllBooks(pageable);
        return ResponseEntity.ok(bookPage.getContent());
    }

    // 도서 한 권 상세 조회 (GET /api/books/{bookId})
    @Override
    @GetMapping("/{bookId}")
    public ResponseEntity<BookResponse> getBookById(@PathVariable("bookId") Long bookId) {
        // 구현 로직 (서비스 호출 등)
        return bookService.findBookById(bookId)
                // Book 엔티티를 DTO로 변환 후, ResponseEntity에 담아 반환
                .map(BookResponse::from)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    //사용자의 재고 조회
    @GetMapping("/{bookId}/stock")
    public ResponseEntity<Integer> getBookStock(@PathVariable Long bookId) {
        // 구현 로직: bookId를 사용하여 해당 도서의 현재 재고 수량을 조회
        // 예: return ResponseEntity.ok(bookService.getStockQuantity(bookId));
        return bookService.findBookById(bookId)
                .map(book -> {
                    boolean inStock = Boolean.TRUE.equals(book.getStockCheckedAt());
                    return ResponseEntity.ok(inStock ? 1 : 0);
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/bulk")
    // 조회 목적이지만 다량의 ID 리스트를 요청 본문에 담아 보내야 하므로 POST 요청을 보냄
    public ResponseEntity<List<GetBookResponse>> getBooksBulk(@RequestBody List<Long> bookIds) {
        // bookIds에는 [1, 5, 22, 100] 처럼 여러 개가 들어옵니다.
        // POST 요청은 데이터를 **요청 본문(Request Body)**에 담아 보낼 수 있습니다.
        List<GetBookResponse> response = bookService.getBooksBulk(bookIds);
        return ResponseEntity.ok(response);
    }
}