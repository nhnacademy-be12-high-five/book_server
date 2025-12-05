package com.nhnacademy.book_server.controller;

import com.nhnacademy.book_server.annotation.CurrentMember;
import com.nhnacademy.book_server.dto.request.ReviewCreateRequest;
import com.nhnacademy.book_server.dto.request.ReviewUpdateRequest;
import com.nhnacademy.book_server.dto.response.*;
import com.nhnacademy.book_server.entity.MemberPrincipal;
import com.nhnacademy.book_server.service.ReviewService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.http.HttpStatus;

import java.util.List;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api")
public class ReviewController {

    private final ReviewService reviewService;

    // 리뷰 작성
    @PostMapping("/books/{bookId}/reviews")
    public ResponseEntity<ReviewCreateResponse> createReview(@Valid @RequestBody ReviewCreateRequest request,
                                                              @PathVariable Long bookId,
                                                             @AuthenticationPrincipal MemberPrincipal principal,
                                                             List<MultipartFile> images){
        Long memberId = principal.getMemberId();
        ReviewCreateResponse response = reviewService.saveReview(request, bookId, memberId, images);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    // 책에 해당하는 리뷰 리스트를 조회
    @GetMapping("/books/{bookId}/reviews")
    public ResponseEntity<Page<BookReviewResponse>> getReviews(@PathVariable Long bookId,
                                                               @PageableDefault(sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable){

        Page<BookReviewResponse> responseList = reviewService.getReviewList(bookId, pageable);
        return ResponseEntity.status(200).body(responseList);
    }

    // 책 리뷰들 페이지에서 보여줄 나의 리뷰 단건 조회
    @GetMapping("/books/{bookId}/reviews/me")
    public ResponseEntity<BookReviewResponse> getMyReview(@PathVariable Long bookId,
                                                          @CurrentMember Long memberId){
        BookReviewResponse response = reviewService.getMyReview(bookId, memberId);
        if (response == null) {
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.status(200).body(response);
    }

    // 마이 페이지에서 보여줄 나의 리뷰 리스트 조회
    @GetMapping("/members/reviews")
    public ResponseEntity<Page<MyPageReviewResponse>> getMyReviews(@CurrentMember Long memberId,
                                                                   @PageableDefault(sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable){
        Page<MyPageReviewResponse> responseList = reviewService.getMyReviewList(memberId, pageable);
        return ResponseEntity.status(200).body(responseList);
    }

    // 리뷰 수정
    @PutMapping("/books/{bookId}/reviews/{reviewId}")
    public ResponseEntity<UpdateReviewResponse> updateMyReview(@PathVariable Long bookId,
                                                              @PathVariable Long reviewId,
                                                              @CurrentMember Long memberId,
                                                              @RequestPart("review") ReviewUpdateRequest request,
                                                              @RequestPart(value = "images", required = false) List<MultipartFile> images){
        UpdateReviewResponse response = reviewService.updateReview(request, bookId, reviewId, memberId, images);

        return ResponseEntity.status(200).body(response);
    }

    // 요구사항에 삭제는 못하게 하지만 특별한 경우(환불) 관리자가 삭제 할 수 있게 하기 위해 구현
    @DeleteMapping("/reviews/{reviewId}")
    public ResponseEntity<Void> removeReview(@PathVariable Long reviewId) {
        reviewService.removeReview(reviewId);
        return ResponseEntity.noContent().build();
    }
}