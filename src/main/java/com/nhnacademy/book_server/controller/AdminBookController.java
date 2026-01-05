package com.nhnacademy.book_server.controller;

import com.nhnacademy.book_server.controller.swagger.BookSwagger;
import com.nhnacademy.book_server.dto.BookInfoDto;
import com.nhnacademy.book_server.dto.BookResponse;
import com.nhnacademy.book_server.dto.request.BookUpdateRequest;
import com.nhnacademy.book_server.service.BookRegistrationService;
import com.nhnacademy.book_server.service.BookService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@Tag(name = "도서 API - 관리자", description = "관리자를 위한 도서 API 입니다.")
@RequestMapping("/api/admin/books")
@RequiredArgsConstructor
// 관리자 권한 책 컨트롤러
public class AdminBookController implements BookSwagger {

    private final BookService bookService;
    private final BookRegistrationService bookRegistrationService;

    // 북 생성
    @PostMapping
    public ResponseEntity<BookInfoDto> createBook(@RequestBody BookInfoDto dto) {
        log.info("관리자 도서 등록 요청 - ISBN: {}, 제목: {}", dto.getIsbn(), dto.getTitle());
        bookService.createBook(dto);
        return ResponseEntity.status(HttpStatus.CREATED).body(dto);
    }

    // 도서 전체 조회
    @GetMapping
    public ResponseEntity<Page<BookResponse>> getAllBooks(@PageableDefault(size = 10) Pageable pageable) {
        Page<BookResponse> bookPage = bookService.findAllBooks(pageable);
        return ResponseEntity.accepted().body(bookPage);
    }

    // 책 한권 조회
    @GetMapping("/{id}")
    public ResponseEntity<BookResponse> getBookById(@PathVariable("id") Long bookId) {
        BookResponse response = bookService.findBookById(bookId);
        return ResponseEntity.ok(response);
    }

    // 책 한권 수정
    @PutMapping("/{id}")
    public ResponseEntity<BookResponse> updateBook(@PathVariable("id") Long bookId,
                                                   @RequestBody BookUpdateRequest updateDto){
        log.info("도서 수정 요청 받음 - ID: {}, Body: {}", bookId, updateDto);
        BookResponse updatedResponse=bookService.updateBook(bookId, updateDto);

        log.info("도서 수정 응답 전송 - Response: {}", updatedResponse);
        return ResponseEntity.ok(updatedResponse); // 200 OK
    }

    @GetMapping("/search-api")
    public ResponseEntity<BookInfoDto> searchBookWithAi(@RequestParam String isbn) {
        log.info("AI 도서 정보 검색 요청 -ISBN: {}", isbn);
        BookInfoDto dto = bookRegistrationService.getBookInfoWithAi(isbn);
        return ResponseEntity.ok(dto);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteBook(@PathVariable("id") Long bookId) {
        log.info("관리자 도서 삭제 요청 - ID: {}", bookId);
        bookService.deleteBook(bookId);
        return ResponseEntity.noContent().build();
    }
}
