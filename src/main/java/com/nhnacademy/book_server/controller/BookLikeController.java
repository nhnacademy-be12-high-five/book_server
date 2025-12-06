package com.nhnacademy.book_server.controller;

import com.nhnacademy.book_server.controller.swagger.UserBookLikeSwagger;
import com.nhnacademy.book_server.dto.BookResponse;
import com.nhnacademy.book_server.entity.BookLike;
import com.nhnacademy.book_server.service.BookLikeService;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api")
// 좋아요 컨트롤러
public class BookLikeController implements UserBookLikeSwagger {

    private final BookLikeService bookLikeService;

    public BookLikeController(BookLikeService bookLikeService) {
        this.bookLikeService = bookLikeService;
    }

    //  도서 좋아요 토글 (등록/취소)
    @Override
    @PostMapping("/api/books/{bookId}/likes")
    public ResponseEntity<Void> toggleLike(@PathVariable Long bookId,
                                           @RequestHeader("X-USER-ID") Long memberId) {

        // 서비스에게 토글 로직 위임
        bookLikeService.toggleLike(bookId, memberId);

        return ResponseEntity.ok().build();
    }

// 마이페이지 - 좋아요 누른 도서 목록 조회
    @Override
    @GetMapping("/api/my-page/likes")
    public ResponseEntity<List<BookResponse>> getMyLikedBooks( @RequestHeader("X-USER-ID") Long memberId, Pageable pageable) {
        return null;
    }
}
