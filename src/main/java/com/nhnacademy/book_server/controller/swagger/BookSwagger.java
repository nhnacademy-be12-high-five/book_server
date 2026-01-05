package com.nhnacademy.book_server.controller.swagger;

import com.nhnacademy.book_server.dto.BookInfoDto;
import com.nhnacademy.book_server.dto.BookResponse;
import com.nhnacademy.book_server.dto.request.BookUpdateRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.validation.constraints.Pattern;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

// 관리자 도서  API
public interface BookSwagger {

    // 도서 생성
    @Operation(summary = "관리자 새로운 도서 생성", description = "도서 정보를 입력 받아 신규 도서를 데이터베이스에 저장합니다.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "도서 생성 성공 (Created)"),
            @ApiResponse(responseCode = "400", description = "잘못된 요청 (유효성 검사 실패)"),
            @ApiResponse(responseCode = "409", description = "이미 존재하는 ISBN")
    })
    @PostMapping
    ResponseEntity<BookInfoDto> createBook(@RequestBody BookInfoDto dto);

    // 도서 전체 조회
    @Operation(summary = "관리자 도서 조회",description = "도서를 조회합니다.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201",description = "도서 생성 성공"),
            @ApiResponse(responseCode = "403",description = "도서 권한이 없음"),
            @ApiResponse(responseCode = "404",description = "도서 추가할 수 없음")
    })
    @GetMapping
    ResponseEntity<Page<BookResponse>> getAllBooks(@PageableDefault(size = 10) Pageable pageable);

     //--------------------------

    // 도서 한권 조회
    @Operation(summary = "관리자 도서 한권 조회",description = "도서를 조회합니다.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201",description = "도서 생성 성공"),
//            @ApiResponse(responseCode = "403",description = "도서 권한이 없음"),
//            @ApiResponse(responseCode = "404",description = "도서 추가할 수 없음")
    })

    @GetMapping("/{id}")
    public ResponseEntity<BookResponse> getBookById(@PathVariable("id") Long bookId);

    // 책 한권 수정
    @Operation(summary = "관리자 도서 수정",description = "도서를 조회합니다.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200",description = "도서 수정 성공 (OK)"),
    })

    @PutMapping("/{id}")
    ResponseEntity<BookResponse> updateBook(@PathVariable Long bookId,
                                    BookUpdateRequest updateDto);

    @Operation(summary = "ISBN으로 AI 기반 도서 정보 조회", description = "Google Books API와 Gemini AI를 활용하여 도서 정보를 조회합니다.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "도서 정보 조회 성공"),
            @ApiResponse(responseCode = "400", description = "유효하지 않은 ISBN 형식"),
            @ApiResponse(responseCode = "404", description = "도서를 찾을 수 없음")
    })
    @GetMapping("/search-api")
    ResponseEntity<BookInfoDto> searchBookWithAi(@RequestParam @Pattern(regexp = "^(\\d{10}|\\d{13})$", message = "ISBN은 10자리 또는 13자리 숫자여야 합니다.") String isbn);


//    // 도서 삭제
//    @Operation(summary = "관리자 도서 삭제",description = "도서를 삭제합니다.")
//
    @DeleteMapping("/{id}")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200",description = "도서 삭제 성공 (OK)"),
//            @ApiResponse(responseCode = "403",description = "관리자 권한이 없음 (Forbidden)"),
//            @ApiResponse(responseCode = "404",description = "삭제하려는 도서를 찾을 수 없음 (Not Found)")
    })

    ResponseEntity<Void> deleteBook(@PathVariable Long bookId);
}
