package com.nhnacademy.book_server.controller;

import com.nhnacademy.book_server.controller.swagger.UserBookSwagger;
import com.nhnacademy.book_server.dto.BookResponse;
import com.nhnacademy.book_server.dto.response.GetBookResponse;
import com.nhnacademy.book_server.repository.BookRepository;
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
@RequestMapping("/api")
@Tag(name = "도서 API - 사용자", description = "사용자를 위한 도서 API 입니다.")
@RequiredArgsConstructor
public class UserBookController implements UserBookSwagger {

    private final BookService bookService;
    private final BookRepository bookRepository;

    // 도서 전체 조회 (GET /api/books)
    @Override
    @GetMapping
    public ResponseEntity<Page<BookResponse>> getAllBooks(@RequestHeader(value = "X-USER-ID", required = false) Long memberId,
                                                          @PageableDefault(size = 10) Pageable pageable) {
        // [수정 1] getContent() 대신 Page 객체 그대로 반환
        // 프론트엔드에서 totalElements, totalPages를 알 수 있게 됩니다.
        Page<BookResponse> bookPage = bookService.findAllBooks(pageable);
        return ResponseEntity.ok(bookPage);
    }

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

    // 사용자의 재고 조회
    // todo 재고 설정을 해야할것같은데
    @GetMapping("/books/{book-Id}/stock")
    public ResponseEntity<Integer> getBookStock(@PathVariable("book-Id") Long bookId) {
        // [수정 3] 비즈니스 로직을 Service로 이동
        // 컨트롤러는 "요청 받고 응답 주는" 역할만 해야 합니다.
        int stock = bookService.getBookStock(bookId);
        return ResponseEntity.ok(stock);
    }

    @PostMapping("/books/bulk")
    public ResponseEntity<List<GetBookResponse>> getBooksBulk(@RequestBody List<Long> bookIds) {
        List<GetBookResponse> response = bookService.getBooksBulk(bookIds);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/books/new")
    public ResponseEntity<List<BookResponse>> getNewBooks() {
        List<BookResponse> books = bookService.getNewBooks();
        return ResponseEntity.ok(books);
    }

    @GetMapping("/books/popular")
    public ResponseEntity<List<BookResponse>> getWeeklyPopular(){
        List<BookResponse> books = bookService.getWeeklyPopularBooks();
        System.out.println("컨트롤러 호출됨! 찾은 책 개수: " + books.size());
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
    public ResponseEntity<List<BookResponse>> getBestSeller(){
        List<BookResponse> BestSellers=bookService.getBestSeller(10);
        return ResponseEntity.ok(BestSellers);
    }
}
