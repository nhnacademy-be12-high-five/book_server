package com.nhnacademy.book_server.controller;

import com.nhnacademy.book_server.controller.swagger.bookSwagger;
import com.nhnacademy.book_server.entity.Book;
import com.nhnacademy.book_server.entity.BookUpdateRequest;
import com.nhnacademy.book_server.parser.ParsingDto;
import com.nhnacademy.book_server.service.BookService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
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

    private final BookService bookService;

    // 북 생성
    @PostMapping
    public ResponseEntity<Book> createBook(@RequestBody ParsingDto parsingDto,
                                           @RequestHeader("X-User-Id") String userId){

        Book savedBook=bookService.createBook(parsingDto);
        return new ResponseEntity<>(savedBook, HttpStatus.CREATED);
    }

//    // 도서 전체 조회
    @GetMapping
    public ResponseEntity<List<Book>> getAllBooks(String userId) {
        List<Book> books = bookService.findAllBooks(userId);
        return ResponseEntity.ok(books); // 200 OK
    }
//
//    // 책 한권 조회
    @GetMapping("/{id}")
    public ResponseEntity<List<Book>> getAllBooksId(@PathVariable Long bookId,@RequestHeader("X-User-Id") String userId) {
        try {
            Optional<Book> book = bookService.findBookById(bookId,userId);

            if (book.isEmpty()) {
                return ResponseEntity.notFound().build(); // 404 Not Found
            }

            return new ResponseEntity<>(HttpStatus.OK);
        }
        catch (RuntimeException e){
            return ResponseEntity.notFound().build();
        }
    }
//
    // 책 한권 수정
    @PutMapping("/{id}")
    public ResponseEntity<Book> updateBook(@PathVariable Long bookId, @RequestBody BookUpdateRequest updateDto, @RequestHeader("X-User-Id") String userId){
        try {
            Book updatedBook = bookService.updateBook(bookId,updateDto,userId);
            return ResponseEntity.ok(updatedBook); // 200 OK
        } catch (RuntimeException e) {
            return ResponseEntity.notFound().build(); // 404 Not Found (책을 찾을 수 없을 때)
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteBook(@PathVariable Long bookId, @RequestHeader("X-User-Id") String userId){
        try {
            bookService.deleteBook(bookId,userId);
            return ResponseEntity.ok().build();
        } catch (RuntimeException e) {
            return ResponseEntity.notFound().build(); // 404 Not Found (책을 찾을 수 없을 때)
        }
    }
}
