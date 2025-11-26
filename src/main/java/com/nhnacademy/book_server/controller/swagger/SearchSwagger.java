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
import org.springframework.web.bind.annotation.PathVariable;
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
    @GetMapping("/search")
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

    //전체 도서 조회
    @Operation(
            summary = "전체 도서 조회",
            description ="페이징을 이용해 전체 도서 목록을 조회합니다."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "조회 성공 (OK)")
    })
    @GetMapping
    ResponseEntity<Page<BookResponse>> getAllBooks(
            @Parameter(description = "페이지 번호 (0부터 시작)", example = "0")
            @RequestParam(required = false, defaultValue = "0") int page,

            @Parameter(description = "페이지당 조회 건수", example = "20")
            @RequestParam(required = false, defaultValue = "20") int size
    );

    //단일 도서 조회
    @Operation(
            summary = "단일 도서 조회",
            description = "도서 ID(식별자)를 기준으로 단일 도서를 조회합니다."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "조회 성공 (OK)"),
            @ApiResponse(responseCode = "404", description = "해당 ID의 도서를 찾을 수 없음 (Not Found)")
    })
    @GetMapping("/{bookId}")
    ResponseEntity<BookResponse> getBookById(
            @Parameter(description = "도서 ID", example = "1")
            @PathVariable("bookId") Long id
    );
}
