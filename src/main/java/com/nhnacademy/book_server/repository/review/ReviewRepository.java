package com.nhnacademy.book_server.repository.review;

import com.nhnacademy.book_server.entity.Review;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ReviewRepository extends JpaRepository<Review, Long> {

    // 이미지 배치 처리는 yml 설정으로 100개 한번에 가져오도록 설정
    Page<Review> findByBookId(Long bookId, Pageable pageable);

    Review findByMemberIdAndBookId(Long memberId, Long bookId);
    // Review 가져올 때 책 정보도 가져오게 처리
    @EntityGraph(attributePaths = "book")
    Page<Review> findByMemberId(Long memberId, Pageable pageable);

    boolean existsByBookIdAndMemberId(Long bookId, Long memberId);

    List<Review> findByBookIdIn(List<Long> bookIds);

    @Modifying
    @Query("UPDATE Review r SET r.likeCount = r.likeCount + 1 WHERE r.id = :reviewId")
    void increaseLikeCount(@Param("reviewId") Long reviewId);

    @Modifying
    @Query("UPDATE Review r SET r.likeCount = r.likeCount - 1 WHERE r.id = :reviewId AND r.likeCount > 0")
    void decreaseLikeCount(@Param("reviewId") Long reviewId);

    long countByBookId(Long bookId);

    @Query("SELECT r.reviewContent FROM Review r WHERE r.book.id = :bookId ORDER BY r.createdAt DESC")
    List<String> findReviewContentsByBookId(@Param("bookId") Long bookId, Pageable pageable);

    @Query("SELECT COALESCE(AVG(r.rating), 0.0) FROM Review r WHERE r.book.id = :bookId")
    Double getAverageRating(@Param("bookId")Long bookId);
}
