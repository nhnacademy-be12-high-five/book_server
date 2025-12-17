package com.nhnacademy.book_server.repository.review;

import com.nhnacademy.book_server.entity.ReviewLike;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ReviewLikeRepository extends JpaRepository<ReviewLike, Long> {
    Optional<ReviewLike> findByMemberIdAndReviewId(Long memberId, Long reviewId);

    @Query("SELECT rl.review.id FROM ReviewLike rl " +
            "WHERE rl.memberId = :memberId AND rl.review.id IN :reviewIds")
    List<Long> findReviewIdsByMemberIdAndReviewIds(@Param("memberId") Long memberId,
                                                   @Param("reviewIds") List<Long> reviewIds);
}
