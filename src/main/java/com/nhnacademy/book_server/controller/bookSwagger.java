package com.nhnacademy.book_server.controller;

import com.nhnacademy.book_server.entity.book;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import org.springframework.http.ResponseEntity;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.annotation.*;

import java.util.List;

// 관리자 도서  API
public interface bookSwagger{

    // 도서 생성
    @Operation(summary = "관리자 새로운 도서 생성", description = "도서 정보를 받아 신규 도서를 데이터베이스에 저장합니다.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "도서 생성 성공 (Created)"),
            @ApiResponse(responseCode = "400", description = "도서 정보가 유효하지 않음 (Bad Request)"),
            @ApiResponse(responseCode = "403", description = "관리자 권한이 없음 (Forbidden)"),
    })
    @PostMapping
    ResponseEntity<book> createBook(@RequestBody book book,@Parameter(hidden = true) @RequestHeader("X-User-Id") Long userId);

    // 도서 전체 조회
    @Operation(summary = "관리자 도서 조회",description = "도서를 조회합니다.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201",description = "도서 생성 성공"),
            @ApiResponse(responseCode = "403",description = "도서 권한이 없음"),
            @ApiResponse(responseCode = "404",description = "도서 추가할 수 없음")
    })
    @GetMapping
    ResponseEntity<List<book>> getAllBooks();

    // 도서 한권 조회
    @Operation(summary = "관리자 도서 한권 조회",description = "도서를 조회합니다.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201",description = "도서 생성 성공"),
            @ApiResponse(responseCode = "403",description = "도서 권한이 없음"),
            @ApiResponse(responseCode = "404",description = "도서 추가할 수 없음")
    })
    @GetMapping("/{id}")
    ResponseEntity<List<book>> getAllBooks(@PathVariable int bookId,@Parameter(hidden = true) @RequestHeader("X-User-Id") Long userId);

    // 책 한권 수정
    @Operation(summary = "관리자 도서 수정",description = "도서를 조회합니다.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200",description = "도서 수정 성공 (OK)"),
            @ApiResponse(responseCode = "403",description = "관리자 권한이 없음 (Forbidden)"),
            @ApiResponse(responseCode = "404",description = "수정하려는 도서를 찾을 수 없음 (Not Found)")
    })

    @PutMapping("/{id}")
    ResponseEntity<book> updateBook(@PathVariable int bookId, @Parameter(hidden = true) @RequestHeader("X-User-Id") Long userId);

    // 도서 삭제
    @Operation(summary = "관리자 도서 삭제",description = "도서를 삭제합니다.")

    @DeleteMapping("/{id}")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200",description = "도서 삭제 성공 (OK)"),
            @ApiResponse(responseCode = "403",description = "관리자 권한이 없음 (Forbidden)"),
            @ApiResponse(responseCode = "404",description = "삭제하려는 도서를 찾을 수 없음 (Not Found)")
    })

    ResponseEntity<Void> deleteBook(@PathVariable int bookId, @Parameter(hidden = true) @RequestHeader("X-User-Id") Long userId);
}
