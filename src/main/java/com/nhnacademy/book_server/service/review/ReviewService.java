package com.nhnacademy.book_server.service.review;

import com.nhnacademy.book_server.dto.request.ReviewCreateRequest;
import com.nhnacademy.book_server.dto.request.ReviewUpdateRequest;
import com.nhnacademy.book_server.dto.response.BookReviewResponse;
import com.nhnacademy.book_server.dto.response.MyPageReviewResponse;
import com.nhnacademy.book_server.dto.response.ReviewCreateResponse;
import com.nhnacademy.book_server.dto.response.UpdateReviewResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface ReviewService {
    ReviewCreateResponse saveReview(ReviewCreateRequest request, Long bookId, Long memberId, List<MultipartFile> images);
    Page<BookReviewResponse> getReviewList(Long bookId, Pageable pageable, Long memberId);
    BookReviewResponse getMyReview(Long memberId, Long bookId);
    Page<MyPageReviewResponse> getMyReviewList(Long memberId, Pageable pageable);
    Page<BookReviewResponse> getCachedReviewPage(Long bookId, Pageable pageable);
    UpdateReviewResponse updateReview(ReviewUpdateRequest request, Long bookId, Long reviewId, Long memberId, List<MultipartFile> images);
    boolean toggleReviewLike(Long reviewId, Long memberId, Long bookId);
}
