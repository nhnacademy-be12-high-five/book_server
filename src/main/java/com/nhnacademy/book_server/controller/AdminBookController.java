package com.nhnacademy.book_server.controller;

import com.nhnacademy.book_server.entity.book;
import com.nhnacademy.book_server.service.bookService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;

@RestController
@Tag(name = "도서 API - 관리자", description = "관리자를 위한 도서 API 입니다.")
@RequestMapping("/api/admin")
@RequiredArgsConstructor

// 관리자 권한 책 컨트롤러
public class AdminBookController implements bookSwagger{

    private final bookService bookService;


    // 북 생성
    @PostMapping
    public ResponseEntity<book> createBook(@RequestBody book book,@RequestHeader("X-User-Id") Long userId){
        // 권한 인증을 위한 userId
        checkAdminPermission(userId);
        book savedBook=bookService.createBook(book);
        return new ResponseEntity<>(savedBook, HttpStatus.CREATED);
    }

    // 도서 전체 조회
    @GetMapping
    public ResponseEntity<List<book>> getAllBooks() {
        List<book> books = bookService.findAllBooks();
        return ResponseEntity.ok(books); // 200 OK
    }

    // 책 한권 조회
    @GetMapping("/{id}")
    public ResponseEntity<List<book>> getAllBooks(@PathVariable int bookId,@RequestHeader("X-User-Id") Long userId) {
        try {
            Optional<book> book = bookService.findBookById(bookId);

            if (book.isEmpty()) {
                return ResponseEntity.notFound().build(); // 404 Not Found
            }

            return new ResponseEntity<>(HttpStatus.OK);
        }
        catch (RuntimeException e){
            return ResponseEntity.notFound().build();
        }
    }

    // 책 한권 수정
    @PutMapping("/{id}")
    public ResponseEntity<book> updateBook(@PathVariable int bookId,@RequestHeader("X-User-Id") Long userId){
        try {
            book updatedBook = bookService.updateBook(bookId);
            return ResponseEntity.ok(updatedBook); // 200 OK
        } catch (RuntimeException e) {
            return ResponseEntity.notFound().build(); // 404 Not Found (책을 찾을 수 없을 때)
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteBook(@PathVariable int bookId,@RequestHeader("X-User-Id") Long userId){
        try {
            bookService.deleteBook(bookId);
            return ResponseEntity.ok().build();
        } catch (RuntimeException e) {
            return ResponseEntity.notFound().build(); // 404 Not Found (책을 찾을 수 없을 때)
        }
    }

    private void checkAdminPermission(Long userId) {
        // 💡 userId를 사용하여 관리자 권한이 있는지 확인하는 로직 (bookService나 AuthService에 위임)
        if (!bookService.isAdmin(userId)) {
            // 403 Forbidden 응답을 반환하기 위해 예외를 발생시킵니다.
            throw new AccessDeniedException("관리자 권한이 없습니다.");
        }
    }
}
