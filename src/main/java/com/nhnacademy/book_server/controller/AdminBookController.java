package com.nhnacademy.book_server.controller;

import com.nhnacademy.book_server.controller.swagger.bookSwagger;
import com.nhnacademy.book_server.dto.BookResponse;
import com.nhnacademy.book_server.dto.request.BookUpdateRequest;
import com.nhnacademy.book_server.entity.Book;
import com.nhnacademy.book_server.parser.ParsingDto;
import com.nhnacademy.book_server.service.BookService;

import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Optional;

@RestController
@Tag(name = "도서 API - 관리자", description = "관리자를 위한 도서 API 입니다.")
@RequestMapping("/api/admin")
@RequiredArgsConstructor
// 관리자 권한 책 컨트롤러
public class AdminBookController implements bookSwagger{

    private final BookService bookService;
//    private final DataParsingService dataParsingService; // [1] 대용량 저장 서비스 주입
//    private final CsvBookParser csvBookParser;           // [2] 파서 주입
//
//    @PostMapping(value = "/books/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
//    public ResponseEntity<String> uploadBookCsv(@RequestPart("file") MultipartFile file) {
//        if (file.isEmpty()) {
//            return ResponseEntity.badRequest().body("파일이 비어있습니다.");
//        }
//
//        File tempFile = null;
//        try {
//            // 1. MultipartFile -> java.io.File 변환 (Parser가 File을 요구하므로)
//            // 임시 파일 생성
//            tempFile = File.createTempFile("upload_", ".csv");
//            file.transferTo(tempFile);
//
//            // 2. 파싱 실행 (File -> List<ParsingDto>)
//            List<ParsingDto> parsingList = csvBookParser.parsing(tempFile);
//
//            // 3. DB 저장 서비스 호출 (List<ParsingDto> -> DB)
//            dataParsingService.saveAll(parsingList);
//
//            return ResponseEntity.ok("성공적으로 업로드 및 저장이 완료되었습니다. (처리 건수: " + parsingList.size() + "건)");
//
//        } catch (IOException e) {
//            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("파일 처리 중 오류가 발생했습니다.");
//        } finally {
//            // 4. 임시 파일 삭제 (리소스 정리)
//            if (tempFile != null && tempFile.exists()) {
//                tempFile.delete();
//            }
//        }
//    }

    // 북 생성
    @PostMapping
    public ResponseEntity<Book> createBook(@RequestBody ParsingDto parsingDto,
                                           @RequestHeader("X-User-Id") String userId){

        Book savedBook=bookService.createBook(parsingDto);
        return new ResponseEntity<>(savedBook, HttpStatus.CREATED);
    }

//    // 도서 전체 조회
    @GetMapping
    public ResponseEntity<List<BookResponse>> getAllBooks(@RequestHeader("X-USER-ID") String userId,
                                                  @PageableDefault(size = 10) Pageable pageable) {
        // 책을 한번에 로드 하기 위한 pagenation 추가
        Page<Book> bookPage=bookService.findAllBooks(pageable);

        Page<BookResponse> bookResponsePage=bookPage.map(BookResponse::from);
        // map을 사용해서 book을 BookResponse로 변환

        return ResponseEntity.ok(bookResponsePage.getContent());
    }
//
//    // 책 한권 조회
    @GetMapping("/{id}")
    public ResponseEntity<BookResponse> getAllBookById(@PathVariable("id") Long bookId,
                                               @RequestHeader("X-User-Id") String userId) {

        return bookService.findBookById(bookId)
                .map(BookResponse::from)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }
//
    // 책 한권 수정
    @PutMapping("/{id}")
    public ResponseEntity<BookResponse> updateBook(@PathVariable("id") Long bookId,
                                                   @RequestBody BookUpdateRequest updateDto,
                                           @RequestHeader("X-User-Id") String userId){
        try {
            Book updatedBook=bookService.updateBook(bookId,updateDto);
            BookResponse updatedResponse=BookResponse.from(updatedBook);
            return ResponseEntity.ok(updatedResponse); // 200 OK

        } catch (RuntimeException e) {
            return ResponseEntity.notFound().build(); // 404 Not Found (책을 찾을 수 없을 때)
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteBook(@PathVariable("id") Long bookId, @RequestHeader("X-User-Id") String userId){
        try {
            bookService.deleteBook(bookId,userId);
            return ResponseEntity.ok().build();
        } catch (RuntimeException e) {
            return ResponseEntity.notFound().build(); // 404 Not Found (책을 찾을 수 없을 때)
        }
    }
}
