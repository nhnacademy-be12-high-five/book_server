package com.nhnacademy.book_server.controller;

import com.nhnacademy.book_server.controller.swagger.UserBookLikeSwagger;
import com.nhnacademy.book_server.dto.BookResponse;
import com.nhnacademy.book_server.entity.BookLike;
import com.nhnacademy.book_server.repository.BookLikeRepository;
import com.nhnacademy.book_server.service.BookLikeService;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@RestController
// 좋아요 컨트롤러
public class BookLikeController implements UserBookLikeSwagger {

    private final BookLikeService bookLikeService;
    private final BookLikeRepository bookLikeRepository;

    public BookLikeController(BookLikeService bookLikeService, BookLikeRepository bookLikeRepository) {
        this.bookLikeService = bookLikeService;
        this.bookLikeRepository = bookLikeRepository;
    }

    //  도서 좋아요 토글 (등록/취소)
    @Override
    @PostMapping("/{bookId}/likes")
    public ResponseEntity<Void> toggleLike(@PathVariable Long bookId,
                                           @RequestHeader("X-USER-ID") Long memberId) {

        // 서비스에게 토글 로직 위임
        bookLikeService.toggleLike(bookId, memberId);
        return ResponseEntity.ok().build();
    }

    // 마이페이지 - 좋아요 누른 도서 목록 조회
    @Override
    @GetMapping("/my-page/likes")
    // /members/me/likes
    public ResponseEntity<List<BookResponse>> getMyLikedBooks( @RequestHeader("X-USER-ID") Long memberId, Pageable pageable) {

        List<BookResponse> likedBooks = bookLikeService.getMyLikedBooks(memberId, pageable);
        return ResponseEntity.ok(likedBooks);
    }
}
