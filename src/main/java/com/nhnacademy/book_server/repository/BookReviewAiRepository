package com.nhnacademy.book_server.repository;

import com.nhnacademy.book_server.entity.BookReviewAi;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface BookReviewAiRepository extends JpaRepository<BookReviewAi, Long> {
    @Query("SELECT r.reviewContent FROM Review r WHERE r.book.id = :bookId ORDER BY r.createdAt DESC")
    List<String> findReviewContentsByBookId(@Param("bookId") Long bookId, Pageable pageable);
    long countByBookId(Long bookId);

    Optional<BookReviewAi> findByBook_Id(Long bookId);
}
