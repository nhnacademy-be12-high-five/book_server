package com.nhnacademy.book_server.controller.swagger;


import com.nhnacademy.book_server.dto.BookResponse;
import com.nhnacademy.book_server.entity.Book;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 일반 사용자 도서 조회 API 인터페이스
 * 사용자 권한으로 도서를 조회하는 기능을 정의합니다.
 */
public interface UserBookSwagger {

    /**
     * 도서 전체 조회
     * 일반 사용자는 모든 도서를 조회할 수 있습니다.
     */
    @Operation(summary = "사용자 도서 전체 조회", description = "시스템에 등록된 모든 도서 목록을 조회합니다.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "도서 목록 조회 성공 (OK)",
                    content = @Content(mediaType = "app" +
                            "lication/json",
                            schema = @Schema(implementation = List.class))),
//            @ApiResponse(responseCode = "404", description = "등록된 도서가 없음 (Not Found)")
    })
//    @GetMapping("/api/books") // 사용자용 API 경로 예시
    ResponseEntity<List<BookResponse>> getAllBooks(@RequestHeader("X-USER-ID") Long memberId,
                                                   @PageableDefault(size = 10) Pageable pageable);

    /**
     * 도서 한 권 상세 조회
     * 특정 ID를 가진 도서의 상세 정보를 조회합니다.
     *
     * @param bookId 조회할 도서의 ID
     */

    @Operation(summary = "사용자 도서 한 권 상세 조회", description = "특정 ID에 해당하는 도서의 상세 정보를 조회합니다.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "도서 상세 정보 조회 성공 (OK)",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = Book.class))),
//            @ApiResponse(responseCode = "404", description = "해당 ID의 도서를 찾을 수 없음 (Not Found)")
    })

//    @GetMapping("/api/books/{bookId}") // 사용자용 API 경로 예시
    ResponseEntity<BookResponse> getBookById(
            @Parameter(description = "조회할 도서의 고유 ID", required = true, example = "1",hidden = true)
            @PathVariable("bookId") Long bookId);

    // 검색 기능 등을 추가할 수 있습니다. (예: 제목, 저자, ISBN 등으로 검색)
    // @Operation(summary = "도서 검색", description = "키워드와 검색 조건에 따라 도서를 검색합니다.")
    // @GetMapping("/api/books/search")
    // ResponseEntity<List<book>> searchBooks(@RequestParam String keyword, @RequestParam String type);

    @Operation(summary = "사용자 도서 재고 수량 조회", description = "특정 도서의 현재 재고 수량을 조회합니다.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "재고 수량 조회 성공 (OK)",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(type = "integer", example = "50"))), // 응답은 숫자 (재고 수량)
            @ApiResponse(responseCode = "404", description = "해당 ID의 도서를 찾을 수 없음 (Not Found)")
    })

    default ResponseEntity<Integer> getBookStock(@PathVariable int bookId,@Parameter(hidden = true) @RequestBody Book book) {
        // 구현 로직: bookId를 사용하여 해당 도서의 현재 재고 수량을 조회
        // 예: return ResponseEntity.ok(bookService.getStockQuantity(bookId));
        // todo 책 재고 확인하는 메서드
        return null;
    }
}