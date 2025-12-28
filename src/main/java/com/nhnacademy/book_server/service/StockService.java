package com.nhnacademy.book_server.service;

import com.nhnacademy.book_server.dto.request.StockRequest;

import java.util.List;

public interface StockService {

    void holdStock(Long bookId, Integer quantity, String idempotencyKey);

    void releaseHeldStock(List<Long> bookIds, String orderKey);

    void confirmStockDeduction(String orderKey, List<Long> bookIds);


    // 여러 책을 한 번에 선점
    void holdStockBatch(List<StockRequest> requests, String orderKey);

    // 여러 책의 재고를 수량 포함하여 복구
    void restoreStock(List<StockRequest> requests, String idempotencyKey);
}