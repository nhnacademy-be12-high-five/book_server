package com.nhnacademy.book_server.repository;

import com.nhnacademy.book_server.entity.StockHeld;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface StockHeldRepository extends JpaRepository<StockHeld, Long> {

    Optional<StockHeld> findByIdempotencyKey(String idempotencyKey);

    List<StockHeld> findAllByOrderKeyAndBook_IdIn(String orderKey, List<Long> bookIds);


    // 선점 되어있는 특정 책의 총 수량 조회
    @Query("SELECT COALESCE(SUM(s.quantity), 0) FROM StockHeld s WHERE s.book.id = :bookId")
    Integer sumHeldQuantityByBookId(@Param("bookId") Long bookId);

    @Query("SELECT s.book.id, SUM(s.quantity) FROM StockHeld s WHERE s.book.id IN :bookIds GROUP BY s.book.id")
    List<Object[]> sumHeldQuantityByBookIds(@Param("bookIds") List<Long> bookIds);
}