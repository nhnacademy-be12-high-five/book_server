package com.nhnacademy.book_server.controller;

import com.nhnacademy.book_server.dto.request.StockRequest;
import com.nhnacademy.book_server.service.StockService;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/books")
public class StockController {

    private final StockService stockService;

    // 1. TCC Try: 재고 선점 (POST /api/books/{bookId}/stock/hold)
    // (기존 API 유지 - 단건 호출용)

    @PostMapping("/{bookId}/stock/hold")
    public ResponseEntity<Void> holdStock(@PathVariable("bookId") Long bookId,
                                          @RequestParam("quantity") @NotNull @Min(1) Integer quantity,
                                          @RequestHeader("Idempotency-Key") String idempotencyKey) {

        stockService.holdStock(bookId, quantity, idempotencyKey);
        return ResponseEntity.ok().build();
    }

    // 1-1. TCC Try (Batch): 재고 일괄 선점 (POST /api/books/stock/hold/batch)
    @PostMapping("/stock/hold/batch")
    public ResponseEntity<Void> holdStockBatch(@RequestBody List<StockRequest> requests,
                                               @RequestParam("orderKey") String orderKey) {

        stockService.holdStockBatch(requests, orderKey);
        return ResponseEntity.ok().build();
    }

    // 2. TCC Cancel: 재고 선점 해제 (POST /api/books/release-stock)
    @PostMapping("/release-stock")
    public ResponseEntity<Void> releaseHeldStock(@RequestBody List<Long> bookIds,
                                                 @RequestParam("orderKey") String orderKey) {

        stockService.releaseHeldStock(bookIds, orderKey);
        return ResponseEntity.ok().build();
    }

    // 3. TCC Confirm: 재고 확정 차감 (POST /api/books/stock/confirm-deduction)
    @PostMapping("/stock/confirm-deduction")
    public ResponseEntity<Void> confirmStockDeduction(@RequestBody List<Long> bookIds,
                                                      @RequestParam("orderKey") String orderKey) {
        // 서비스의 변경된 시그니처에 맞춰 수정
        stockService.confirmStockDeduction(orderKey, bookIds);
        return ResponseEntity.ok().build();
    }

    // 4. TCC Cancel: 재고 복구 (WAITING 취소 시) (POST /api/books/stock/restore)
    @PostMapping("/stock/restore")
    public ResponseEntity<Void> restoreStock(@RequestBody List<StockRequest> requests,
                                             @RequestHeader("Idempotency-Key") String idempotencyKey) {

        // 서비스의 변경된 시그니처에 맞춰 수정 (수량 정보 포함)
        stockService.restoreStock(requests, idempotencyKey);
        return ResponseEntity.ok().build();
    }
}