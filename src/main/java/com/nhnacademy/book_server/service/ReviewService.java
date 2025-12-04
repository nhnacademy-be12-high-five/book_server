package com.nhnacademy.book_server.service;

import com.nhnacademy.book_server.dto.request.ReviewCreateRequest;
import com.nhnacademy.book_server.dto.request.ReviewUpdateRequest;
import com.nhnacademy.book_server.dto.response.*;
import com.nhnacademy.book_server.entity.MemberPrincipal;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface ReviewService {
    ReviewCreateResponse saveReview(ReviewCreateRequest request, Long bookId, Long memberId, List<MultipartFile> images);
    Page<BookReviewResponse> getReviewList(Long bookId, Pageable pageable);
    BookReviewResponse getMyReview(Long memberId, Long bookId);
    Page<MyPageReviewResponse> getMyReviewList(Long memberId, Pageable pageable);
    void removeReview(Long reviewId);
    UpdateReviewResponse updateReview(ReviewUpdateRequest request, Long bookId, Long reviewId, Long memberId, List<MultipartFile> images);
}
