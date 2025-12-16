package com.nhnacademy.book_server.controller;

import com.nhnacademy.book_server.service.StockService;
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
    @PostMapping("/{bookId}/stock/hold")
    public ResponseEntity<Void> holdStock(@PathVariable("bookId") Long bookId,
                                          @RequestParam("quantity") Integer quantity,
                                          @RequestHeader("Idempotency-Key") String idempotencyKey) {

        stockService.holdStock(bookId, quantity, idempotencyKey);
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
    public ResponseEntity<Void> confirmStockDeduction(@RequestBody List<Long> bookIds) {
        stockService.confirmStockDeduction(bookIds);
        return ResponseEntity.ok().build();
    }

    // 4. TCC Cancel: 재고 복구 (WAITING 취소 시) (POST /api/books/stock/restore)
    @PostMapping("/stock/restore")
    public ResponseEntity<Void> restoreStock(@RequestBody List<Long> bookIds,
                                             @RequestHeader("Idempotency-Key") String idempotencyKey) {

        stockService.restoreStock(bookIds, idempotencyKey);
        return ResponseEntity.ok().build();
    }
}