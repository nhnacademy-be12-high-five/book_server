package com.nhnacademy.book_server.service.review.impl;

import com.nhnacademy.book_server.dto.common.RestPage;
import com.nhnacademy.book_server.dto.event.ReviewCreatedEvent;
import com.nhnacademy.book_server.dto.event.ReviewDeletedEvent;
import com.nhnacademy.book_server.dto.event.ReviewImageDeleteEvent;
import com.nhnacademy.book_server.dto.request.ReviewCreateRequest;
import com.nhnacademy.book_server.dto.request.ReviewUpdateRequest;
import com.nhnacademy.book_server.dto.response.*;
import com.nhnacademy.book_server.entity.Book;
import com.nhnacademy.book_server.entity.Review;
import com.nhnacademy.book_server.entity.ReviewImage;
import com.nhnacademy.book_server.entity.ReviewLike;
import com.nhnacademy.book_server.exception.BusinessException;
import com.nhnacademy.book_server.exception.ErrorCode;
import com.nhnacademy.book_server.feign.MemberFeignClient;
import com.nhnacademy.book_server.feign.OrderFeignClient;
import com.nhnacademy.book_server.repository.BookRepository;
import com.nhnacademy.book_server.repository.review.ReviewImageRepository;
import com.nhnacademy.book_server.repository.review.ReviewLikeRepository;
import com.nhnacademy.book_server.repository.review.ReviewRepository;
import com.nhnacademy.book_server.service.MinioImageService;
import com.nhnacademy.book_server.service.review.ReviewService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.Duration;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Slf4j
public class ReviewServiceImpl implements ReviewService {

    private final ReviewRepository reviewRepository;
    private final ReviewImageRepository reviewImageRepository;
    private final MinioImageService imageUploadService;
    private final ApplicationEventPublisher eventPublisher;
    private final OrderFeignClient orderFeignClient;
    private final MemberFeignClient memberFeignClient;
    private final BookRepository bookRepository;
    private final ReviewLikeRepository reviewLikeRepository;
    private final StringRedisTemplate redisTemplate;


    private final ReviewService self;
    private static final int MAX_IMAGE_COUNT = 5;

    @Autowired // 스프링 4.3+부터 단일 생성자라면 생략 가능하지만, 명시적으로 작성
    public ReviewServiceImpl(ReviewRepository reviewRepository,
                             ReviewImageRepository reviewImageRepository,
                             MinioImageService imageUploadService,
                             ApplicationEventPublisher eventPublisher,
                             OrderFeignClient orderFeignClient,
                             MemberFeignClient memberFeignClient,
                             BookRepository bookRepository,
                             ReviewLikeRepository reviewLikeRepository,
                             StringRedisTemplate redisTemplate,
                             @Lazy ReviewService self) { // Self Reference에 Lazy 적용
        this.reviewRepository = reviewRepository;
        this.reviewImageRepository = reviewImageRepository;
        this.imageUploadService = imageUploadService;
        this.eventPublisher = eventPublisher;
        this.orderFeignClient = orderFeignClient;
        this.memberFeignClient = memberFeignClient;
        this.bookRepository = bookRepository;
        this.reviewLikeRepository = reviewLikeRepository;
        this.redisTemplate = redisTemplate;
        this.self = self;
    }

    // 리뷰 작성 기능
    @Override
    @Transactional
    public ReviewCreateResponse saveReview(ReviewCreateRequest request,
                                           Long bookId,
                                           Long memberId,
                                           List<MultipartFile> images) {
        boolean isPurchased = Boolean.TRUE.equals(orderFeignClient.hasPurchasedBook(memberId, bookId));

        if (!isPurchased) {
            throw new BusinessException(ErrorCode.REVIEW_WRITE_AUTHOR);
        }

        // 중복 작성
        if (reviewRepository.existsByBookIdAndMemberId(bookId, memberId)) {
            throw new BusinessException(ErrorCode.REVIEW_DUP);
        }

        Book book = bookRepository.findById(bookId)
                .orElseThrow(() -> new BusinessException(ErrorCode.BOOK_NOT_FOUND));

        Review review = new Review(request.rating(), request.content(),
                book, memberId);

        reviewRepository.save(review);

        int newImageCount = (images != null) ? images.stream().filter(img -> !img.isEmpty()).toList().size() : 0;

        if (newImageCount > MAX_IMAGE_COUNT) {
            throw new BusinessException(ErrorCode.REVIEW_IMAGE_LIMIT_EXCEEDED);
        }

        imageSave(images, review);

        // 리뷰 포인트 증가
        if (newImageCount > 0) {
            eventPublisher.publishEvent(
                    new ReviewCreatedEvent(memberId, bookId, "EARN_PHOTO_REVIEW")
            );
        } else {
            eventPublisher.publishEvent(
                    new ReviewCreatedEvent(memberId, bookId, "EARN_REVIEW")
            );
        }

        return new ReviewCreateResponse(review.getId(), request.rating(), request.content());
    }

    @Override
    @Transactional(readOnly = true)
    public Page<BookReviewResponse> getReviewList(Long bookId, Pageable pageable, Long memberId) {

        // @Cacheable 붙은 메서드 호출
        Page<BookReviewResponse> cachedPage = self.getCachedReviewPage(bookId, pageable);

        // 비회원이면 바로 캐싱 페이지 반환 끝
        if (memberId == null || cachedPage.isEmpty()) {
            return cachedPage;
        }

        // 현재 페이지에 있는 리뷰 아이디만 뽑음
        List<Long> reviewIds = cachedPage.getContent().stream()
                .map(BookReviewResponse::reviewId)
                .toList();

        // 로그인 회원이 누른 좋아요누른 리뷰 아이디만 뽑아냄
        Set<Long> myLikedReviewIds = new HashSet<>(
                reviewLikeRepository.findReviewIdsByMemberIdAndReviewIds(memberId, reviewIds)
        );

        // map -> 캐싱 된 리뷰에서 안에 있는 요소만 바꿈
        Page<BookReviewResponse> personalizedPage = cachedPage.map(response -> {
            if (myLikedReviewIds.contains(response.reviewId())) {
                return response.withIsLiked(true);
            }
            return response;
        });

        return new RestPage<>(personalizedPage);
    }

    // 같은 책 같은 페이지 같은 사이즈 같은 결과 , 리뷰 없으면 캐시 x
    @Override
    @Cacheable(value = "bookReviews", key = "#bookId + '_' + #pageable.pageNumber", unless = "#result.isEmpty()")
    public Page<BookReviewResponse> getCachedReviewPage(Long bookId, Pageable pageable) {
        Page<Review> reviews = reviewRepository.findByBookId(bookId, pageable);

        Map<Long, String> memberMap = getMemberNicknames(reviews);

        Page<BookReviewResponse> page = reviews.map(review -> {
            String name = memberMap.getOrDefault(review.getMemberId(), "알 수 없음");
            String maskedName = maskName(name);

            List<ReviewImageResponse> reviewImages = review.getReviewImages().stream()
                    .map(img -> new ReviewImageResponse(img.getId(), img.getFileUrl()))
                    .toList();

            return new BookReviewResponse(
                    review.getId(),
                    review.getMemberId(),
                    maskedName,
                    review.getReviewContent(),
                    review.getRating(),
                    review.getCreatedAt(),
                    reviewImages,
                    review.getLikeCount(),
                    false
            );
        });
        return new RestPage<>(page);
    }

    // 마스킹 처리 메서드
    private String maskName(String name) {
        if (name == null || name.isBlank()) {
            return "알 수 없음";
        }

        int length = name.length();

        if (length == 2) {
            return name.charAt(0) + "*";
        }

        if (length >= 3) {
            char firstChar = name.charAt(0);
            char lastChar = name.charAt(length - 1);

            String mask = "*".repeat(length - 2);
            return firstChar + mask + lastChar;
        }
        return "*";
    }


    // 내가 쓴 리뷰를 그 책에 들어갔을 때 확인하기 위한 단건 조회
    @Override
    @Transactional(readOnly = true)
    public BookReviewResponse getMyReview(Long bookId, Long memberId) {
        Review myReview = reviewRepository.findByMemberIdAndBookId(memberId, bookId);

        if (myReview == null) {
            return null;
        }

        List<ReviewImageResponse> reviewImages = myReview.getReviewImages().stream()
                .map(img -> new ReviewImageResponse(img.getId(), img.getFileUrl()))
                .toList();

        return new BookReviewResponse(
                myReview.getId(),
                myReview.getMemberId(),
                null,
                myReview.getReviewContent(),
                myReview.getRating(),
                myReview.getCreatedAt(),
                reviewImages,
                myReview.getLikeCount(),
                null
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
            String title = (book != null) ? book.getTitle() : "삭제된 도서";

            return new MyPageReviewResponse(
                    review.getId(),
                    bookId,
                    title,
                    review.getCreatedAt()
            );
        });
    }

    // 리뷰 수정
    @Override
    @Transactional
    public UpdateReviewResponse updateReview(ReviewUpdateRequest request, Long bookId, Long reviewId,
                                             Long memberId, List<MultipartFile> images) {
        // 예외 처리들
        Review review = reviewRepository.findById(reviewId)
                .orElseThrow(() -> new BusinessException(ErrorCode.REVIEW_NOT_FOUND));

        if (!review.getBook().getId().equals(bookId)) {
            throw new BusinessException(ErrorCode.REVIEW_NOT_MATCH_BOOK);
        }

        if (!memberId.equals(review.getMemberId())) {
            throw new BusinessException(ErrorCode.REVIEW_NOT_AUTHOR);
        }

        // 원래 포토리뷰였던건지 확인
        boolean wasPhotoReview = !review.getReviewImages().isEmpty();

        List<ReviewImage> imagesToDelete = new ArrayList<>();
        List<Long> deleteImageIds = request.deleteImageIds();

        // 삭제 해야 할 이미지를 아이디를 이용해 찾음 + 다른 리뷰 이미지 삭제 방지
        if (deleteImageIds != null && !deleteImageIds.isEmpty()) {
            imagesToDelete = reviewImageRepository.findAllById(deleteImageIds);
            imagesToDelete.removeIf(img -> !img.getReview().getId().equals(reviewId));
        }

        int currentImageCount = review.getReviewImages().size();
        int deleteCount = imagesToDelete.size();
        int newImageCount = (images != null) ? (int) images.stream().filter(img -> !img.isEmpty()).count() : 0;

        if (currentImageCount - deleteCount + newImageCount > MAX_IMAGE_COUNT) {
            throw new BusinessException(ErrorCode.REVIEW_IMAGE_LIMIT_EXCEEDED);
        }

        review.update(request.rating(), request.content());

        // 실제 삭제 db, 메모리 상태 같게
        if (!imagesToDelete.isEmpty()) {
            // db 삭제
            reviewImageRepository.deleteAll(imagesToDelete);
            // 영속성 컨텍스트 동기화
            review.getReviewImages().removeAll(imagesToDelete);
        }

        imageSave(images, review);

        int finalImageCount = currentImageCount - deleteCount + newImageCount;
        boolean isNowPhotoReview = finalImageCount > 0;

        if (!wasPhotoReview && isNowPhotoReview) {
            log.info("리뷰 업그레이드 감지 (일반->포토): 차액 포인트 지급 요청 - reviewId: {}", reviewId);
            eventPublisher.publishEvent(new ReviewCreatedEvent(memberId, bookId, "EARN_REVIEW_UPGRADE"));
        }

        if (!imagesToDelete.isEmpty()) {
            List<String> fileUrls = imagesToDelete.stream()
                    .map(ReviewImage::getFileUrl)
                    .toList();

            eventPublisher.publishEvent(new ReviewImageDeleteEvent(fileUrls));
        }

        evictBookReviewCache(bookId);

        return new UpdateReviewResponse(request.content(), request.rating());
    }

    @Override
    @Transactional
    public boolean toggleReviewLike(Long reviewId, Long memberId, Long bookId) {

        // 광클 방지
        String lockKey = "like_lock:" + memberId + ":" + reviewId;
        Boolean isLocked = redisTemplate.opsForValue()
                .setIfAbsent(lockKey, "locked", Duration.ofMillis(500));

        if (Boolean.FALSE.equals(isLocked)) {
            throw new BusinessException(ErrorCode.TOO_MANY_REQUESTS);
        }

        try {
            Review review = reviewRepository.findById(reviewId)
                    .orElseThrow(() -> new BusinessException(ErrorCode.REVIEW_NOT_FOUND));

            if (review.getMemberId().equals(memberId)) {
                return false;
            }

            Optional<ReviewLike> existingLike = reviewLikeRepository.findByMemberIdAndReviewId(memberId, reviewId);

            boolean isLiked;

            if (existingLike.isPresent()) {
                reviewLikeRepository.delete(existingLike.get());
                reviewRepository.decreaseLikeCount(reviewId);
                isLiked = false;
            } else {
                ReviewLike newLike = new ReviewLike(review, memberId);
                reviewLikeRepository.save(newLike);
                reviewRepository.increaseLikeCount(reviewId);
                isLiked = true;
            }

            return isLiked;
        } finally {
            redisTemplate.delete(lockKey);
        }
    }

    private void evictBookReviewCache(Long bookId) {
        if (bookId == null) return;

        String pattern = "bookReviews::" + bookId + "_*";

        ScanOptions options = ScanOptions.scanOptions().match(pattern).count(100).build();
        try (Cursor<String> cursor = redisTemplate.scan(options)) {
            List<String> keysToDelete = new ArrayList<>();
            cursor.forEachRemaining(keysToDelete::add);
            if (!keysToDelete.isEmpty()) {
                redisTemplate.delete(keysToDelete);
                log.info("캐시 삭제 완료 bookId: {} /{}개", bookId, keysToDelete.size());
            }
        }
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
                review.getReviewImages().addAll(newImages);
            }
        }
    }

    // 회원 닉네임 조회 헬퍼
    private Map<Long, String> getMemberNicknames(Page<Review> reviews) {
        List<Long> memberIds = reviews.getContent().stream()
                .map(Review::getMemberId)
                .distinct()
                .toList();

        if (memberIds.isEmpty()) return Collections.emptyMap();

        try {
            List<MemberResponse> responses = memberFeignClient.getMembersInfo(memberIds);
            if (responses == null) return Collections.emptyMap();
            return responses.stream().collect(Collectors.toMap(
                    MemberResponse::memberId, MemberResponse::name, (a, b) -> a));
        } catch (Exception e) {
            log.error("Member Service Error", e);
            return Collections.emptyMap();
        }
    }

    @Transactional
    public void removeReview(Long reviewId, Long memberId) {
        Review review = reviewRepository.findById(reviewId)
                .orElseThrow(() -> new BusinessException(ErrorCode.REVIEW_NOT_FOUND));

        Long bookId = review.getBook().getId();

        // (정책) 작성자/관리자 권한 체크는 여기서 처리

        // 이미지 삭제 이벤트(기존 주석 코드에 있던 흐름 유지)
        List<String> imageUrls = review.getReviewImages().stream()
                .map(ReviewImage::getFileUrl)
                .toList();

        if (!imageUrls.isEmpty()) {
            eventPublisher.publishEvent(new ReviewImageDeleteEvent(imageUrls));
        }

        reviewRepository.delete(review);

        // 🔥 핵심: 삭제 이벤트 발행
        eventPublisher.publishEvent(new ReviewDeletedEvent(memberId, bookId));

        // 리뷰 리스트 캐시도 쓰고 있으면 삭제(이미 updateReview에서 evictBookReviewCache 사용 중)
        evictBookReviewCache(bookId);
    }

}