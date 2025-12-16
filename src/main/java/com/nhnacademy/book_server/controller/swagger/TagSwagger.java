package com.nhnacademy.book_server.controller.swagger;

import com.nhnacademy.book_server.dto.request.TagRequest;
import com.nhnacademy.book_server.dto.response.TagResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;

import java.util.List;

@Tag(name = "Tag API", description = "도서 태그 등록 및 조회 API")
public interface TagSwagger {

    @Operation(summary = "태그 등록", description = "새로운 태그를 생성합니다.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "태그 등록 성공"),
            @ApiResponse(responseCode = "400", description = "잘못된 요청 (중복된 태그 등)")
    })

    ResponseEntity<TagResponse> createTag(
            @Parameter(description = "등록할 태그 정보", required = true)
            @RequestBody TagRequest tagRequest
    );

    @Operation(summary = "태그 목록 조회", description = "모든 태그 리스트를 조회합니다.")
    @ApiResponse(responseCode = "200", description = "조회 성공")
    ResponseEntity<List<TagResponse>> getTags();
}
