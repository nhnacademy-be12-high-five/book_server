package com.nhnacademy.book_server.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;

public record ReviewUpdateRequest(@NotBlank @Size(max = 1000, min = 10, message = "리뷰 내용은 1000자 이하, 10자 이상이어야 합니다.") String content,
                                  @Min(1) @Max(5) int rating,
                                  List<Long> deleteImageIds) {}
