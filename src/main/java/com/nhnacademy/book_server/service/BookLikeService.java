package com.nhnacademy.book_server.service;

import com.nhnacademy.book_server.entity.Book;
import com.nhnacademy.book_server.entity.BookLike;
import com.nhnacademy.book_server.exception.BusinessException;
import com.nhnacademy.book_server.exception.MemberNotFoundException;
import com.nhnacademy.book_server.repository.BookLikeRepository;
import com.nhnacademy.book_server.repository.BookRepository;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import org.springframework.data.jdbc.core.JdbcAggregateOperations;
import org.springframework.stereotype.Service;

@Service
public class BookLikeService{

    private final BookRepository bookRepository;
    private final BookLikeRepository bookLikeRepository;

    public BookLikeService(BookRepository bookRepository, BookLikeRepository bookLikeRepository) {
        this.bookRepository = bookRepository;
        this.bookLikeRepository = bookLikeRepository;
    }

    // todo 책 좋아요 서비스 작성하기
    public void toggleLike(Long bookId, Long memberId){

        // 멤버인지 아닌지 체크
        memberId = 1L;
        if (memberId != null){
            // 책의 아이디가 있는지 없는지 체크
            bookRepository.findById(bookId)
                    .orElseThrow(()->new RuntimeException("첵의 아이디가 존재하지 않습니다."));

            if (bookLikeRepository.existsByBookIdAndMemberId(bookId,memberId)){
                // 이미 좋아요를 누른 상태라면
                bookLikeRepository.deleteByBookIdAndMemberId(bookId,memberId);
            }
            else{
                // 좋아요가 눌러지지 않은 상태라면
                BookLike bookLike=new BookLike();
                bookLikeRepository.save(bookLike);
            }
        }
    }
}
