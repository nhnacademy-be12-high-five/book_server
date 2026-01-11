package com.nhnacademy.book_server.controller;

import com.nhnacademy.book_server.controller.swagger.UserBookLikeSwagger;
import com.nhnacademy.book_server.dto.BookResponse;
import com.nhnacademy.book_server.service.BookLikeService;
import lombok.AllArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api")
@AllArgsConstructor
// 좋아요 컨트롤러
public class BookLikeController implements UserBookLikeSwagger {

    private final BookLikeService bookLikeService;

    //  도서 좋아요 토글 (등록/취소)
    @Override
    @PostMapping("/books/{bookId}/likes")
    public ResponseEntity<Boolean> toggleLike(@PathVariable("bookId") Long bookId,
                                              @RequestHeader(value = "X-USER-ID",required = true) Long memberId) {
        // 서비스에게 토글 로직 위임
        bookLikeService.toggleLike(bookId, memberId);
        return ResponseEntity.ok().build();
    }

    // 마이페이지 - 좋아요 누른 도서 목록 조회
    @Override
    @GetMapping("/my-page/likes")
    public ResponseEntity<List<BookResponse>> getMyLikedBooks(@RequestHeader(value = "X-USER-ID",required = true) Long memberId,
                                                               Pageable pageable) {
        List<BookResponse> likedBooks = bookLikeService.getMyLikedBooks(memberId, pageable);
        return ResponseEntity.ok(likedBooks);
    }

    // 상세페이지에서 좋아요를 기억하기 위한 메서드
    @GetMapping("/books/{bookId}/likes")
    public ResponseEntity<Boolean> getLikeStatus(@PathVariable("bookId") Long bookId,
                                                 @RequestHeader(value = "X-USER-ID", required = true) Long memberId) {

        boolean isLiked = bookLikeService.isLiked(bookId, memberId);
        return ResponseEntity.ok(isLiked);
    }

}
