package com.nhnacademy.book_server.repository;

import com.nhnacademy.book_server.entity.BookLike;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface BookLikeRepository extends JpaRepository<BookLike,Long> {
//
//    // 책의 아이디랑 멤버 아이디가 존재하는지 확인하는 메서드
//    boolean existsBookLikeByIdAndMemberId(Long bookId, Long memberId);


    // 먼저 멤버 아이디가 존재하는지 체크
    // 멤버 아이디가 존재하면 북 좋아요를 누를 수 있음
    // 한 책에 좋아요는 한번만 아마 ..?
    // 책 아이디가 있다는건 책의 제목이 있다는 거니까
    // 책의 아이디, 멤버 아이디가 있으면 좋아요를 누를 수 있음
    // 그럼 여기서 saveAll()을 하면 되지 않을까
    // 좋아요가 있으면 마이페이지 찜 목록으로 이동

    boolean existsByBookIdAndMemberId(Long bookId,Long memberId);

    // 좋아요 취소 메서드 추가
    void deleteByBookIdAndMemberId(Long bookId,Long memberId);
}
