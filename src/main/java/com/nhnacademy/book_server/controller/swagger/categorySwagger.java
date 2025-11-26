package com.nhnacademy.book_server.controller.swagger;

import com.nhnacademy.book_server.dto.CategoryResponse;
import com.nhnacademy.book_server.dto.BookResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.Parameter;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.List;

/**
 * 사용자 카테고리 조회 Swagger 인터페이스
 */
public interface CategorySwagger {

    /**
     * 대분류 조회
     */
    @Operation(summary = "대분류 카테고리 조회",
            description = "depth=1 인 상위 카테고리 목록을 조회합니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = CategoryResponse.class)))
    })
    ResponseEntity<List<CategoryResponse>> getParents();


    /**
     * 하위 카테고리 조회
     */
    @Operation(summary = "하위 카테고리 조회",
            description = "특정 카테고리의 하위 카테고리를 조회합니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = CategoryResponse.class))),
            @ApiResponse(responseCode = "404", description = "상위 카테고리 없음")
    })
    ResponseEntity<List<CategoryResponse>> getChilds(
            @Parameter(description = "상위 카테고리 ID", example = "1")
            @PathVariable int categoryId);


    /**
     * 카테고리별 도서 조회
     */
    @Operation(summary = "카테고리별 도서 조회",
            description = "특정 카테고리에 포함된 도서 목록을 조회합니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = BookResponse.class))),
            @ApiResponse(responseCode = "404", description = "카테고리 없음")
    })
    ResponseEntity<List<BookResponse>> getBooksByCategory(
            @Parameter(description = "카테고리 ID", example = "10")
            @PathVariable int categoryId);
}
