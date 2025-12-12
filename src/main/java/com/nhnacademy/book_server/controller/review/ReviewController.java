package com.nhnacademy.book_server.controller.review;

import com.nhnacademy.book_server.dto.request.ReviewCreateRequest;
import com.nhnacademy.book_server.dto.request.ReviewUpdateRequest;
import com.nhnacademy.book_server.dto.response.*;
import com.nhnacademy.book_server.service.ReviewService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.http.HttpStatus;

import java.util.List;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/books")
public class ReviewController {

    private final ReviewService reviewService;

    // 리뷰 작성
    @PostMapping("/{book-id}/reviews")
    public ResponseEntity<ReviewCreateResponse> createReview(@RequestParam Integer rating,
                                                             @RequestParam String content,
                                                             @PathVariable("book-id") Long bookId,
                                                             @RequestHeader("x-user-id") Long memberId,
                                                             @RequestPart(value = "images", required = false) List<MultipartFile> images) {
        ReviewCreateRequest req = new ReviewCreateRequest(rating, content);
        ReviewCreateResponse response = reviewService.saveReview(req, bookId, memberId, images);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    // 책에 해당하는 리뷰 리스트를 조회
    @GetMapping("/{book-id}/reviews")
    public ResponseEntity<Page<BookReviewResponse>> getReviews(@PathVariable("book-id") Long bookId,
                                                               @PageableDefault(sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {

        Page<BookReviewResponse> responseList = reviewService.getReviewList(bookId, pageable);
        return ResponseEntity.status(200).body(responseList);
    }

    // 책 리뷰들 페이지에서 보여줄 나의 리뷰 단건 조회
    @GetMapping("/{book-id}/reviews/me")
    public ResponseEntity<BookReviewResponse> getMyReview(@PathVariable("book-id") Long bookId,
                                                          @RequestHeader(value = "x-user-id") Long memberId) {
        BookReviewResponse response = reviewService.getMyReview(bookId, memberId);
        if (response == null) {
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.status(200).body(response);
    }

    // 마이 페이지에서 보여줄 나의 리뷰 리스트 조회
    @GetMapping("/members/me/reviews")
    public ResponseEntity<Page<MyPageReviewResponse>> getMyReviews(@RequestHeader("x-user-id") Long memberId,
                                                                   @PageableDefault(sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        Page<MyPageReviewResponse> responseList = reviewService.getMyReviewList(memberId, pageable);
        return ResponseEntity.status(200).body(responseList);
    }

    // 리뷰 수정
    @PostMapping(value = "/{book-id}/reviews/{review-id}",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<UpdateReviewResponse> updateReview(
            @PathVariable("book-id") Long bookId,
            @PathVariable("review-id") Long reviewId,
            @RequestHeader("x-user-id") Long memberId,
            @RequestPart("request") ReviewUpdateRequest request,
            @RequestPart(value = "images", required = false) List<MultipartFile> images
    ) {
        UpdateReviewResponse response =
                reviewService.updateReview(request, bookId, reviewId, memberId, images);

        return ResponseEntity.ok(response);
    }

    // 요구사항에 삭제는 못하게 하지만 특별한 경우(환불) 관리자가 삭제 할 수 있게 하기 위해 구현
//    @DeleteMapping("/reviews/{reviewId}")
//    public ResponseEntity<Void> removeReview(@PathVariable Long reviewId) {
//        reviewService.removeReview(reviewId);
//        return ResponseEntity.noContent().build();
//    }
}