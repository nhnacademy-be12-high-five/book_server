package com.nhnacademy.book_server.repository;

import com.nhnacademy.book_server.entity.Review;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReviewRepository extends JpaRepository<Review, Long> {
    Page<Review> findByBookId(Long bookId, Pageable pageable);
    Review findByMemberIdAndBookId(Long memberId, Long bookId);
    // Review 가져올 때 책 정보도 가져오게 처리
    @EntityGraph(attributePaths = "book")
    Page<Review> findByMemberId(Long memberId, Pageable pageable);

    boolean existsByBookIdAndMemberId(Long bookId, Long memberId);
}
