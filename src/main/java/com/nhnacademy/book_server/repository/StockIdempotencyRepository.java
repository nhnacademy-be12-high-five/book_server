package com.nhnacademy.book_server.repository;

import com.nhnacademy.book_server.entity.StockIdempotencyRecord;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StockIdempotencyRepository extends JpaRepository<StockIdempotencyRecord, String> {
    boolean existsByIdempotencyKey(String idempotencyKey);
}