package com.nhnacademy.book_server.service.impl;

import com.nhnacademy.book_server.config.RabbitMqConfig;
import com.nhnacademy.book_server.dto.ReviewCreatedEvent;
import com.nhnacademy.book_server.dto.request.PointEarnRequest;
import com.nhnacademy.book_server.dto.request.ReviewCreateRequest;
import com.nhnacademy.book_server.dto.request.ReviewUpdateRequest;
import com.nhnacademy.book_server.dto.response.*;
import com.nhnacademy.book_server.entity.Book;
import com.nhnacademy.book_server.entity.Review;
import com.nhnacademy.book_server.entity.ReviewImage;
import com.nhnacademy.book_server.exception.BusinessException;
import com.nhnacademy.book_server.exception.ErrorCode;
import com.nhnacademy.book_server.repository.BookRepository;
import com.nhnacademy.book_server.repository.ReviewImageRepository;
import com.nhnacademy.book_server.repository.ReviewRepository;
import com.nhnacademy.book_server.service.ImageService;
import com.nhnacademy.book_server.service.ReviewService;
import jakarta.ws.rs.NotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class ReviewServiceImpl implements ReviewService {

    private final ReviewRepository reviewRepository;
    private final ReviewImageRepository reviewImageRepository;
    private final BookRepository bookRepository;
    private final ImageService imageUploadService;
    private final ApplicationEventPublisher eventPublisher;

    // 리뷰 생성 기능
    @Override
    public ReviewCreateResponse saveReview(ReviewCreateRequest request,
                                           Long bookId,
                                           Long memberId) {

        Book book = bookRepository.findById(bookId).orElseThrow(() -> new BusinessException(ErrorCode.BOOK_NOT_FOUND));

        Review review = new Review(request.rating(), request.content(),
                book, memberId);

        reviewRepository.save(review);

        eventPublisher.publishEvent(new ReviewCreatedEvent(memberId, "EARN_REVIEW"));

        return new ReviewCreateResponse(review.getId(), request.rating(), request.content());
    }

    @Override
    @Transactional(readOnly = true)
    public Page<BookReviewResponse> getReviewList(Long bookId, Pageable pageable) {
        Page<Review> reviews = reviewRepository.findByBookId(bookId, pageable);

        // 이미지 엔티티 리스트를 URL 문자열 리스트로 변환

        return reviews.map(review -> {
            List<String> urls = review.getReviewImages().stream()
                    .map(ReviewImage::getFileUrl)
                    .toList();

            return new BookReviewResponse(
                    review.getId(),
                    review.getMemberId(),
                    review.getReviewContent(),
                    review.getRating(),
                    review.getCreatedAt(),
                    urls
            );
        });
    }

    // 내가 쓴 리뷰를 그 책에 들어갔을 때 확인하기 위한 단건 조회
    @Override
    public BookReviewResponse getMyReview(Long bookId, Long memberId) {
        Review myReview = reviewRepository.findByMemberIdAndBookId(memberId, bookId);

        List<String> urls = myReview.getReviewImages().stream()
                .map(ReviewImage::getFileUrl)
                .toList();

        return new BookReviewResponse(
                myReview.getId(),
                myReview.getMemberId(),
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

            return new MyPageReviewResponse(
                    review.getId(),
                    book.getId(),
                    book.getTitle(),
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
    public UpdateReviewResponse updateReview(ReviewUpdateRequest request, Long reviewId,
                                           Long memberId, List<MultipartFile> images) {
        Review review = reviewRepository.findById(reviewId).orElseThrow(() -> new BusinessException(ErrorCode.REVIEW_NOT_FOUND));

        if(!memberId.equals(review.getMemberId())){
            throw new BusinessException(ErrorCode.REVIEW_NOT_AUTHOR);
        }

        review.update(request.rating(), request.content());

        List<Long> deleteImageIds = request.deleteImageIds();

        if (deleteImageIds != null && !deleteImageIds.isEmpty()) {
            List<ReviewImage> imagesToDelete = reviewImageRepository.findAllById(deleteImageIds);

            // 삭제 전 검증, img id가 review에 속한게 아닌 것들은 삭제해버리는 로직
            imagesToDelete.removeIf(img -> !img.getReview().getId().equals(reviewId));

            List<String> fileUrls = imagesToDelete.stream()
                    .map(ReviewImage::getFileUrl)
                    .toList();

            // minio 에서 삭제
            imageUploadService.deleteImages(fileUrls);
            // db 에서 삭제
            reviewImageRepository.deleteAll(imagesToDelete);
        }

        imageSave(images, review);

        return new UpdateReviewResponse(request.content(), request.rating());
    }

    // 새로운 이미지 저장하는 헬퍼 메서드
    private void imageSave(List<MultipartFile> images, Review review) {
        if (images != null && !images.isEmpty()) {
            List<ReviewImage> newImages = new ArrayList<>();

            for (MultipartFile image : images) {
                if (image.isEmpty()) continue;

                String imageUrl = imageUploadService.uploadImage(image);
                newImages.add(new ReviewImage(review, imageUrl));
            }

            if (!newImages.isEmpty()) {
                reviewImageRepository.saveAll(newImages);
            }

        }
    }
}