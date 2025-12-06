package com.nhnacademy.book_server.service;

import com.nhnacademy.book_server.entity.Book;
import com.nhnacademy.book_server.entity.BookLike;

import com.nhnacademy.book_server.repository.BookLikeRepository;
import com.nhnacademy.book_server.repository.BookRepository;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import org.springframework.data.jdbc.core.JdbcAggregateOperations;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor // 생성자 주입을 롬복으로 처리 (깔끔함)
@Transactional // DB 변경 작업이 있으므로 필수!
public class BookLikeService{

    private final BookRepository bookRepository;
    private final BookLikeRepository bookLikeRepository;

    // todo 책 좋아요 서비스 작성하기
    public void toggleLike(Long bookId, Long memberId) {

        // 1. memberId 확인 (null 체크)
        if (memberId == null) {
            throw new RuntimeException("회원이 존재하지 않습니다."); // throw 추가
        }

        // 2. 책 존재 여부 확인
        Book book = bookRepository.findById(bookId)
                .orElseThrow(() -> new RuntimeException("책의 아이디가 존재하지 않습니다."));

        // 3. 토글 로직
        if (bookLikeRepository.existsByBookIdAndMemberId(bookId, memberId)) {
            // 이미 좋아요가 있다면 -> 삭제
            bookLikeRepository.deleteByBookIdAndMemberId(bookId, memberId);
        } else {
            // 좋아요가 없다면 -> 생성 및 저장
            BookLike bookLike = BookLike.builder()
                    .book(book)
                    .memberId(memberId)
                    .build();

            bookLikeRepository.save(bookLike);
        }
    }


}
