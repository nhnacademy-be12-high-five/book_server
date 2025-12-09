package com.nhnacademy.book_server.controller.swagger;

import com.nhnacademy.book_server.dto.BookSortType;
import com.nhnacademy.book_server.dto.BookResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

// 도서 검색 API Swagger 인터페이스
public interface SearchSwagger {

    @Operation(
            summary = "도서 검색",
            description = """
                    도서명, 저자, 출판사, ISBN, 태그, 도서 설명 등을 기준으로 도서를 검색합니다.

                    정렬 기준(sort)은 다음 중 하나를 사용합니다.
                    - POPULAR   : 인기도 (조회수/검색수 등을 조합한 점수 기준)
                    - NEW       : 신상품 (발행일 내림차순)
                    - LOW_PRICE : 최저가 (가격 오름차순)
                    - HIGH_PRICE: 최고가 (가격 내림차순)
                    - RATING    : 평점 높은 순
                    - REVIEW    : 리뷰 수 많은 순
                    """
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "검색 성공 (OK)"),
            @ApiResponse(responseCode = "400", description = "검색 조건이 유효하지 않음 (Bad Request)")
    })
    @GetMapping
    ResponseEntity<Page<BookResponse>> searchBooks(
            @Parameter(
                    description = "검색어 (도서명, 저자명, 태그 등)",
                    example = "그림책"
            )
            @RequestParam String keyword,

            @Parameter(
                    description = """
                            정렬 기준
                            - POPULAR   : 인기도
                            - NEW       : 신상품
                            - LOW_PRICE : 최저가
                            - HIGH_PRICE: 최고가
                            - RATING    : 평점 높은 순
                            - REVIEW    : 리뷰 수 많은 순
                            """,
                    example = "POPULAR"
            )
            @RequestParam(required = false, defaultValue = "POPULAR") BookSortType sort,

            @Parameter(
                    description = "페이지 번호 (0부터 시작)",
                    example = "0"
            )
            @RequestParam(required = false, defaultValue = "0") int page,

            @Parameter(
                    description = "페이지당 조회 건수",
                    example = "20"
            )
            @RequestParam(required = false, defaultValue = "20") int size
    );

    @Operation(
            summary = "도서 검색 인덱스 재구축",
            description = """
                    DB/파싱된 도서 전체를 Elasticsearch book_index에 다시 색인합니다.
                    - CSV/알라딘 파서가 먼저 실행되어 DB에 도서가 들어가 있어야 합니다.
                    - 검색 가중치/정렬은 BookSearchServiceImpl에서 처리합니다.
                    """
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "인덱싱 완료"),
            @ApiResponse(responseCode = "500", description = "인덱싱 중 오류 발생")
    })
    @PostMapping("/reindex")
    ResponseEntity<String> reindex();
}
