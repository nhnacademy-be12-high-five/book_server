package com.nhnacademy.book_server.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class StockRequest {
    @NotNull(message = "bookId는 필수입니다")
    @Positive(message = "bookId는 양수여야 합니다")
    private Long bookId;

    @NotNull(message = "quantity는 필수입니다")
    @Positive(message = "quantity는 1 이상이어야 합니다")
    private Integer quantity;
}
