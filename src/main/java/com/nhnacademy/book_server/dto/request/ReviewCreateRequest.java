package com.nhnacademy.book_server.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

public record ReviewCreateRequest (@Min(1) @Max(5) int rating, @NotBlank String content){}
