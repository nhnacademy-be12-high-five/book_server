package com.nhnacademy.book_server.service;

import com.nhnacademy.book_server.dto.BookResponse;
import com.nhnacademy.book_server.entity.Book;
import com.nhnacademy.book_server.entity.BookLike;
import com.nhnacademy.book_server.repository.BookLikeRepository;
import com.nhnacademy.book_server.repository.BookRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class BookLikeService{

    private final BookRepository bookRepository;
    private final BookLikeRepository bookLikeRepository;

    public void toggleLike(Long bookId, Long memberId) {

        // 1. memberId 확인 (null 체크)
        if (memberId == null) {
            throw new RuntimeException("회원이 존재하지 않습니다."); // throw 추가
        }

        // 2. 책 존재 여부 확인
        Book book = bookRepository.findById(bookId)
                .orElseThrow(() -> new RuntimeException("책의 아이디가 존재하지 않습니다."));

        // 3. 토글 로직
        if (bookLikeRepository.existsByBook_IdAndMemberId(bookId, memberId)) {
            // 이미 좋아요가 있다면 -> 삭제
            bookLikeRepository.deleteByBook_IdAndMemberId(bookId, memberId);
        }

        else {
            // 좋아요가 없다면 -> 생성 및 저장
            BookLike bookLike = BookLike.builder()
                    .book(book)
                    .memberId(memberId)
                    .build();

            bookLikeRepository.save(bookLike);
        }
    }

// // 마이페이지 - 좋아요 누른 도서 목록 조회
    @Transactional(readOnly = true)
    public List<BookResponse> getMyLikedBooks(Long memberId, Pageable pageable) {

        // 1. DB에서 내 좋아요 목록 조회
        Page<BookLike> likePage = bookLikeRepository.findAllByMemberId(memberId, pageable);

        // 2. BookLike -> BookResponse 변환
        return likePage.stream()
                .map(bookLike -> BookResponse.from(bookLike.getBook()))
                .toList();
    }

    @Transactional(readOnly = true)
    public boolean isLiked(Long bookId, Long memberId) {
        return bookLikeRepository.existsByBook_IdAndMemberId(bookId, memberId);
    }
}
