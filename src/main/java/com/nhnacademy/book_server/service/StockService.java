package com.nhnacademy.book_server.service;

import java.util.List;

public interface StockService {

    void holdStock(Long bookId, Integer quantity, String idempotencyKey);

    void releaseHeldStock(List<Long> bookIds, String orderKey);

    void confirmStockDeduction(List<Long> bookIds);

    void restoreStock(List<Long> bookIds, String idempotencyKey);
}