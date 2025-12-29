package com.nhnacademy.book_server.repository;

import com.nhnacademy.book_server.entity.BookLike;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.transaction.annotation.Transactional;

public interface BookLikeRepository extends JpaRepository<BookLike,Long> {
//
//    // 책의 아이디랑 멤버 아이디가 존재하는지 확인하는 메서드
//    boolean existsBookLikeByIdAndMemberId(Long bookId, Long memberId);

    // 좋아요 취소 메서드 추가
    @Modifying
    @Transactional
    void deleteByBook_IdAndMemberId(Long bookId, Long memberId);

    Page<BookLike> findAllByMemberId(Long memberId, Pageable pageable);

    boolean existsByBook_IdAndMemberId(Long bookId, Long memberId);
}
