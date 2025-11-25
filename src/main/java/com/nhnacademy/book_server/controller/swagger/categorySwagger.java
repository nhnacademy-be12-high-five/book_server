package com.nhnacademy.book_server.controller.swagger;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jdk.jfr.Category;
import org.springframework.http.ResponseEntity;

import java.util.List;

// 카테고리에 따른 책 조회
public interface categorySwagger {

    // 카테고리에 따른 책 조회
        @Operation(summary = "전체 카테고리 및 계층 조회", description = "시스템에 등록된 모든 도서 목록을 조회합니다.")
        @ApiResponses(value = {
                @ApiResponse(responseCode = "200", description = "도서 카테고리 조회 성공 (OK)",
                        content = @Content(mediaType = "application/json",
                                schema = @Schema(implementation = List.class))),
        })
//    @GetMapping("/api/books/category") // 사용자용 API 경로 예시
        ResponseEntity<List<Category>> getAllCategories();

        //----------------------------------------------

        @Operation(summary = "사용자 카테고리 상세 조회", description = "특정 ID에 해당하는 도서의 상세 정보를 조회합니다.")
        @ApiResponses(value = {
                @ApiResponse(responseCode = "200", description = "도서 카테고리 조회 성공 (OK)",
                        content = @Content(mediaType = "application/json",
                                schema = @Schema(implementation = Category.class)))
//            @ApiResponse(responseCode = "404", description = "해당 ID의 도서를 찾을 수 없음 (Not Found)")
        })

       //@GetMapping("/api/categories/{categoryId}")
        ResponseEntity<Category> getCategoryById();

        //     c
//                @Parameter(description = "조회할 카테고리 ID")
//                @PathVariable("categoryId") Long categoryId,
//
//                @Parameter(description = "응답에 상위 카테고리 포함 여부 (true: 상위 2단계까지 포함)")
//                @RequestParam(value = "includeParent", defaultValue = "false", required = false) boolean includeParent,
//
//                @Parameter(description = "포함할 하위 카테고리 계층 깊이 (0: 하위 포함 안 함, 1: 1단계 하위만, 2: 2단계 하위까지)")
//                @RequestParam(value = "childrenDepth", defaultValue = "0", required = false) int childrenDepth
//        );

}
