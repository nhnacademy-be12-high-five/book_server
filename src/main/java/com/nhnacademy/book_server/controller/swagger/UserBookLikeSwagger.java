package com.nhnacademy.book_server.controller.swagger;

import com.nhnacademy.book_server.dto.BookResponse; // 기존에 사용하시던 DTO
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;

import java.util.List;

@Tag(name = "Book-Like", description = "사용자 도서 좋아요(찜하기) 관련 API")
public interface UserBookLikeSwagger {

    /**
     * 1. 도서 좋아요 토글 (등록/취소)
     * 사용자가 특정 도서에 대해 좋아요를 누르거나, 이미 눌렀다면 취소합니다.
     */
    @Operation(summary = "도서 좋아요 토글", description = "특정 도서에 대해 좋아요를 설정하거나 해제합니다. (이미 좋아요 상태면 해제, 아니면 등록)")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "좋아요 상태 변경 성공 (OK)"),
            @ApiResponse(responseCode = "404", description = "해당 ID의 도서를 찾을 수 없음 (Not Found)")
    })
    @PostMapping("/api/books/{bookId}/likes") // POST 매핑 예시
    ResponseEntity<Void> toggleLike(
            @Parameter(description = "좋아요를 누를 도서의 ID", required = true, example = "1")
            @PathVariable("bookId") Long bookId,

            @Parameter(description = "사용자 식별 ID (헤더)", required = true, hidden = true)
            @RequestHeader("X-USER-ID") Long memberId
    );

    /**
     * 2. (구 4번) 마이페이지 - 좋아요 누른 도서 목록 조회
     * 사용자가 좋아요를 누른 도서들의 리스트를 페이징하여 반환합니다.
     */
    @Operation(summary = "내가 좋아요 누른 도서 목록 조회", description = "마이페이지에서 사용자가 좋아요를 누른 도서 리스트를 페이징하여 조회합니다.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "조회 성공",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = List.class))), // List<BookResponse>
            @ApiResponse(responseCode = "404", description = "사용자를 찾을 수 없음")
    })
    @GetMapping("/api/my-page/likes") // GET 매핑 예시
    ResponseEntity<List<BookResponse>> getMyLikedBooks(
            @Parameter(description = "사용자 식별 ID (헤더)", required = true, hidden = true)
            @RequestHeader("X-USER-ID") Long memberId,

            @Parameter(description = "페이징 정보 (page, size 등)")
            @PageableDefault(size = 10) Pageable pageable
    );
}