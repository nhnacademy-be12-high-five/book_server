package com.nhnacademy.book_server.repository;

import com.nhnacademy.book_server.entity.Review;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReviewRepository extends JpaRepository<Review, Long> {
    Page<Review> findByBookId(Long bookId, Pageable pageable);
    Review findByMemberIdAndBookId(Long memberId, Long bookId);
    Page<Review> findByMemberId(Long memberId, Pageable pageable);
}
