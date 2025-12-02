package com.nhnacademy.book_server.controller;

import com.nhnacademy.book_server.controller.swagger.UserBookSwagger;
import com.nhnacademy.book_server.entity.Book;
import com.nhnacademy.book_server.service.BookService;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
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
    public ResponseEntity<List<Book>> getAllBooks() {
        // 구현 로직 (서비스 호출 등)
        List<Book> bookList = bookService.findAllBooks();
        return ResponseEntity.ok(bookList);
    }

    // 도서 한 권 상세 조회 (GET /api/books/{bookId})
    @Override
    @GetMapping("/{bookId}")
    public ResponseEntity<Book> getBookById(@PathVariable("bookId") Long bookId) {
        // 구현 로직 (서비스 호출 등)
        return bookService.findBookById(bookId)
                // 🔴 [수정] 찾은 책(book)을 ok() 안에 넣어줘야 함!
                .map(book -> ResponseEntity.ok(book))
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

//    @PostMapping("/bulk-lookup") // URL 예시: /search-list, /bulk 등
//    public ResponseEntity<List<Book>> getBooksByBulk(@RequestBody List<Long> bookIds) {
//
//        List<Book> bookList = bookService.getBooksByIds(bookIds);
//        return ResponseEntity.ok(bookList);
//    }

}