package com.nhnacademy.book_server.controller;

import com.nhnacademy.book_server.controller.swagger.UserBookLikeSwagger;
import com.nhnacademy.book_server.entity.BookLike;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/book/{id}/like")  // 경로 수정해야할듯

// 좋아요 컨트롤러
public class BookLikeController implements UserBookLikeSwagger {

    // 전체 조회
    @GetMapping
    public List<BookLike> getAllLike(Long bookLikeId, @RequestHeader("X-USER-ID") Long memberId){
        // todo 2 책 좋아요 컨트롤러

    }
}
