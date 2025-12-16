package com.nhnacademy.book_server.repository;

import com.nhnacademy.book_server.entity.StockHeld;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface StockHeldRepository extends JpaRepository<StockHeld, Long> {

    Optional<StockHeld> findByIdempotencyKey(String idempotencyKey);

    List<StockHeld> findAllByOrderKeyAndBook_IdIn(String orderKey, List<Long> bookIds);

    List<StockHeld> findAllByBook_IdIn(List<Long> bookIds);
}