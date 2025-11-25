package com.nhnacademy.book_server.controller;

import com.nhnacademy.book_server.controller.swagger.UserBookSwagger;
import com.nhnacademy.book_server.entity.Book;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/books") // 모든 도서 관련 API의 기본 경로를 지정
@Tag(name = "도서 API - 사용자", description = "사용자를 위한 도서 API 입니다.")
public class UserBookController implements UserBookSwagger {

    // 도서 전체 조회 (GET /api/books)
    @Override
    @GetMapping
    public ResponseEntity<List<Book>> getAllBooks() {
        // 구현 로직 (서비스 호출 등)
        return null;
    }

    // 도서 한 권 상세 조회 (GET /api/books/{bookId})
    @Override
    @GetMapping("/{bookId}")
    public ResponseEntity<Book> getBookById(@PathVariable("bookId") int bookId) {
        // 구현 로직 (서비스 호출 등)
        return null;
    }


    //사용자의 재고 조회
    @GetMapping("/{bookId}/stock")
    public ResponseEntity<Integer> getBookStock(@PathVariable int bookId,@RequestBody Book book) {
        // 구현 로직: bookId를 사용하여 해당 도서의 현재 재고 수량을 조회
        // 예: return ResponseEntity.ok(bookService.getStockQuantity(bookId));
        return null;
    }
}