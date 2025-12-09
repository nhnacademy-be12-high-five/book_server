package com.nhnacademy.book_server.service.impl;

import com.nhnacademy.book_server.dto.ReviewCreatedEvent;
import com.nhnacademy.book_server.dto.request.ReviewCreateRequest;
import com.nhnacademy.book_server.dto.request.ReviewUpdateRequest;
import com.nhnacademy.book_server.dto.response.*;
import com.nhnacademy.book_server.entity.Book;
import com.nhnacademy.book_server.entity.Review;
import com.nhnacademy.book_server.entity.ReviewImage;
import com.nhnacademy.book_server.exception.BusinessException;
import com.nhnacademy.book_server.exception.ErrorCode;
import com.nhnacademy.book_server.feign.MemberFeignClient;
import com.nhnacademy.book_server.feign.OrderFeignClient;
import com.nhnacademy.book_server.repository.ReviewImageRepository;
import com.nhnacademy.book_server.repository.ReviewRepository;
import com.nhnacademy.book_server.service.MinioImageService;
import com.nhnacademy.book_server.service.ReviewService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ReviewServiceImpl implements ReviewService {

    private final ReviewRepository reviewRepository;
    private final ReviewImageRepository reviewImageRepository;
    private final MinioImageService imageUploadService;
    private final ApplicationEventPublisher eventPublisher;
    private final OrderFeignClient orderFeignClient;
    private final MemberFeignClient memberFeignClient;

    private static final int MAX_IMAGE_COUNT = 5;

    @PersistenceContext
    private EntityManager em;

    // 리뷰 생성 기능
    @Override
    @Transactional
    public ReviewCreateResponse saveReview(ReviewCreateRequest request,
                                           Long bookId,
                                           Long memberId,
                                           List<MultipartFile> images) {
        // 구매 여부 체크
        Boolean isPurchased = orderFeignClient.hasPurchasedBook(memberId, bookId);

        // 구매 안한 사람이 접근
        if (Boolean.FALSE.equals(isPurchased)) {
            throw new BusinessException(ErrorCode.REVIEW_WRITE_AUTHOR);
        }

        // 중복 작성
        if (reviewRepository.existsByBookIdAndMemberId(bookId, memberId)) {
            throw new BusinessException(ErrorCode.REVIEW_DUP);
        }

        // 책 Id만 가진 proxy Book 객체 생성
        Book bookRef = em.getReference(Book.class, bookId);

        Review review = new Review(request.rating(), request.content(),
                bookRef, memberId);

        reviewRepository.save(review);

        int newImageCount = (images != null) ? images.stream().filter(img -> !img.isEmpty()).toList().size() : 0;

        if (newImageCount > MAX_IMAGE_COUNT) {
            throw new BusinessException(ErrorCode.REVIEW_IMAGE_LIMIT_EXCEEDED);
        }

        imageSave(images, review);

        // 리뷰 포인트 증가
        eventPublisher.publishEvent(new ReviewCreatedEvent(memberId, "EARN_REVIEW"));

        return new ReviewCreateResponse(review.getId(), request.rating(), request.content());
    }

    // 전체 리뷰 조회
    @Override
    @Transactional(readOnly = true)
    public Page<BookReviewResponse> getReviewList(Long bookId, Pageable pageable) {
        Page<Review> reviews = reviewRepository.findByBookId(bookId, pageable);

        List<Long> memberIds = reviews.getContent().stream()
                .map(Review::getMemberId)
                .distinct()
                .toList();

        Map<Long, String> memberMap;

        if (memberIds.isEmpty()) {
            memberMap = new HashMap<>(); // 빈 맵 할당
        } else {
            List<MemberResponse> memberResponses = memberFeignClient.getMembersInfo(memberIds);

            // List -> Map 변환
            memberMap = memberResponses.stream()
                    .collect(Collectors.toMap(
                            // key
                            MemberResponse::memberId,
                            // value
                            MemberResponse::loginId,
                            // 중복 무시
                            (existing, replacement) -> existing
                    ));
        }

        // default_batch_fetch_size 덕분에 여기서 이미지 조회 쿼리가 'IN' 절로 1번만 나감 (N+1 해결)
        return reviews.map(review -> {
            String loginId = memberMap.getOrDefault(review.getMemberId(), "알 수 없음"); // 탈퇴한 회원 처리

            List<String> urls = review.getReviewImages().stream()
                    .map(ReviewImage::getFileUrl)
                    .toList();

            return new BookReviewResponse(
                    loginId,
                    review.getReviewContent(),
                    review.getRating(),
                    review.getCreatedAt(),
                    urls
            );
        });
    }


    // 내가 쓴 리뷰를 그 책에 들어갔을 때 확인하기 위한 단건 조회
    @Override
    @Transactional(readOnly = true)
    public BookReviewResponse getMyReview(Long bookId, Long memberId) {
        Review myReview = reviewRepository.findByMemberIdAndBookId(memberId, bookId);

        if (myReview == null) {
             return null;
        }

        List<String> urls = myReview.getReviewImages().stream()
                .map(ReviewImage::getFileUrl)
                .toList();

        String loginId = "알 수 없음";

        List<MemberResponse> memberResponses = memberFeignClient.getMembersInfo(List.of(memberId));

        if (memberResponses != null && !memberResponses.isEmpty()) {
            loginId = memberResponses.get(0).loginId();
        }

        return new BookReviewResponse(
                loginId,
                myReview.getReviewContent(),
                myReview.getRating(),
                myReview.getCreatedAt(),
                urls
        );
    }

    // 내가 쓴 리뷰들 조회
    @Override
    @Transactional(readOnly = true)
    public Page<MyPageReviewResponse> getMyReviewList(Long memberId, Pageable pageable) {
        Page<Review> myReviews = reviewRepository.findByMemberId(memberId, pageable);

        return myReviews.map(review -> {
            Book book = review.getBook();

            Long bookId = (book != null) ? book.getId() : null;
            String title= (book != null) ? book.getTitle() : "삭제된 도서";

            return new MyPageReviewResponse(
                    review.getId(),
                    bookId,
                    title,
                    review.getCreatedAt()
            );
        });
    }

    // 특수한 경우 리뷰를 삭제하기 위해 구현
    @Override
    public void removeReview(Long reviewId) {
        Review review = reviewRepository.findById(reviewId)
                .orElseThrow(() -> new BusinessException((ErrorCode.REVIEW_NOT_FOUND)));

        List<String> imageUrls = review.getReviewImages().stream()
                .map(ReviewImage::getFileUrl)
                .toList();

        imageUploadService.deleteImages(imageUrls);

        reviewRepository.delete(review);
    }

    // 리뷰 수정
    @Override
    public UpdateReviewResponse updateReview(ReviewUpdateRequest request, Long bookId, Long reviewId,
                                           Long memberId, List<MultipartFile> images) {
        Review review = reviewRepository.findById(reviewId)
                .orElseThrow(() -> new BusinessException(ErrorCode.REVIEW_NOT_FOUND));

        if (!review.getBook().getId().equals(bookId)) {
            throw new BusinessException(ErrorCode.REVIEW_NOT_MATCH_BOOK);
        }

        if (!memberId.equals(review.getMemberId())) {
            throw new BusinessException(ErrorCode.REVIEW_NOT_AUTHOR);
        }

        List<ReviewImage> imagesToDelete = new ArrayList<>();
        List<Long> deleteImageIds = request.deleteImageIds();

        if (deleteImageIds != null && !deleteImageIds.isEmpty()) {
            imagesToDelete = reviewImageRepository.findAllById(deleteImageIds);
            imagesToDelete.removeIf(img -> !img.getReview().getId().equals(reviewId));
        }

        int currentImageCount = review.getReviewImages().size();
        int deleteCount = imagesToDelete.size();
        int newImageCount = (images != null) ? images.stream().filter(img -> !img.isEmpty()).toList().size() : 0;

        if (currentImageCount - deleteCount + newImageCount > MAX_IMAGE_COUNT) {
            throw new BusinessException(ErrorCode.REVIEW_IMAGE_LIMIT_EXCEEDED);
        }

        review.update(request.rating(), request.content());

        if (!imagesToDelete.isEmpty()) {
            List<String> fileUrls = imagesToDelete.stream()
                    .map(ReviewImage::getFileUrl)
                    .toList();

            imageUploadService.deleteImages(fileUrls);
            reviewImageRepository.deleteAll(imagesToDelete);
        }

        imageSave(images, review);

        return new UpdateReviewResponse(request.content(), request.rating());
    }

    // 새로운 이미지 저장하는 헬퍼 메서드
    private void imageSave(List<MultipartFile> images, Review review) {
        if (images != null && !images.isEmpty()) {
            List<ReviewImage> newImages = images.parallelStream() // 병렬 스트림 사용
                    .filter(image -> !image.isEmpty())
                    .map(image -> {
                        String imageUrl = imageUploadService.uploadImage(image);
                        return new ReviewImage(review, imageUrl);
                    })
                    .toList();

            if (!newImages.isEmpty()) {
                reviewImageRepository.saveAll(newImages);
            }
        }
    }
}