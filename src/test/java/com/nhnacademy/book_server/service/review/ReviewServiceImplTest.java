package com.nhnacademy.book_server.service.review;

import com.nhnacademy.book_server.dto.event.ReviewCreatedEvent;
import com.nhnacademy.book_server.dto.event.ReviewDeletedEvent;
import com.nhnacademy.book_server.dto.event.ReviewImageDeleteEvent;
import com.nhnacademy.book_server.dto.request.ReviewCreateRequest;
import com.nhnacademy.book_server.dto.request.ReviewUpdateRequest;
import com.nhnacademy.book_server.dto.response.BookReviewResponse;
import com.nhnacademy.book_server.dto.response.MemberResponse;
import com.nhnacademy.book_server.dto.response.MyPageReviewResponse;
import com.nhnacademy.book_server.dto.response.ReviewImageResponse;
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
import com.nhnacademy.book_server.service.review.impl.ReviewServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.multipart.MultipartFile;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ReviewServiceImplTest {

    @InjectMocks
    private ReviewServiceImpl reviewService;

    @Mock private ReviewServiceImpl self; // Lazy self-injection mock
    @Mock private ReviewRepository reviewRepository;
    @Mock private ReviewImageRepository reviewImageRepository;
    @Mock private MinioImageService imageUploadService;
    @Mock private ApplicationEventPublisher eventPublisher;
    @Mock private OrderFeignClient orderFeignClient;
    @Mock private MemberFeignClient memberFeignClient;
    @Mock private BookRepository bookRepository;
    @Mock private ReviewLikeRepository reviewLikeRepository;
    @Mock private StringRedisTemplate redisTemplate;

    // Redis Operations Mocks
    @Mock private ValueOperations<String, String> valueOperations;
    @Mock private Cursor<String> cursor;

    private final Long bookId = 1L;
    private final Long memberId = 100L;
    private final Long reviewId = 10L;
    private Book testBook;
    private Review testReview;

    @BeforeEach
    void setUp() {
        // 순환 참조(Lazy Injection) 해결을 위한 Mock 주입
        ReflectionTestUtils.setField(reviewService, "self", self);

        testBook = Book.builder().id(bookId).title("Test Book").build();
        testReview = new Review(5, "Content", testBook, memberId);
        ReflectionTestUtils.setField(testReview, "id", reviewId);
        // 초기 이미지 리스트 (빈 리스트)
        ReflectionTestUtils.setField(testReview, "reviewImages", new ArrayList<>());
    }

    /**
     * Redis Scan 동작을 모킹하는 헬퍼 메서드
     * evictBookReviewCache 메서드 테스트용
     */
    private void setupRedisScanMock() {
        given(redisTemplate.scan(any(ScanOptions.class))).willReturn(cursor);

        // cursor.forEachRemaining 동작 정의: 1개의 키("bookReviews::1_0")를 찾았다고 가정
        doAnswer(invocation -> {
            Consumer<String> consumer = invocation.getArgument(0);
            consumer.accept("bookReviews::" + bookId + "_0");
            return null;
        }).when(cursor).forEachRemaining(any(Consumer.class));
    }

    // ============================
    // 1. saveReview (리뷰 작성)
    // ============================

    @Test
    @DisplayName("작성 실패: 이미 작성한 리뷰 존재 (REVIEW_DUP)")
    void saveReview_Fail_Duplicate() {
        // Given
        // 1. 구매 권한 체크를 통과하도록 설정 (이 부분이 누락되어 REVIEW_WRITE_AUTHOR 발생)
        given(orderFeignClient.hasPurchasedBook(anyLong(), anyLong())).willReturn(true);

        // 2. 이미 작성한 리뷰가 있다고 설정
        given(reviewRepository.existsByBookIdAndMemberId(bookId, memberId)).willReturn(true);

        ReviewCreateRequest request = new ReviewCreateRequest(5, "Content");

        // When & Then
        assertThatThrownBy(() -> reviewService.saveReview(request, bookId, memberId, null))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.REVIEW_DUP);
    }

    @Test
    @DisplayName("작성 실패: 이미지 개수 초과 (5개 초과)")
    void saveReview_Fail_ImageLimit() {
        // Given
        given(orderFeignClient.hasPurchasedBook(anyLong(), anyLong())).willReturn(true);

        // 2. 중복 작성 아님 설정
        given(reviewRepository.existsByBookIdAndMemberId(bookId, memberId)).willReturn(false);

        // 3. 책 존재 설정
        given(bookRepository.findById(bookId)).willReturn(Optional.of(testBook));

        // 6개의 이미지 생성
        List<MultipartFile> images = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            images.add(new MockMultipartFile("img", "test.jpg", "image/jpeg", "data".getBytes()));
        }
        ReviewCreateRequest request = new ReviewCreateRequest(5, "Content");

        // When & Then
        assertThatThrownBy(() -> reviewService.saveReview(request, bookId, memberId, images))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.REVIEW_IMAGE_LIMIT_EXCEEDED);
    }

    @Test
    @DisplayName("작성 성공: 이미지 포함 (EARN_PHOTO_REVIEW 이벤트 발행)")
    void saveReview_Success_WithImage() {
        // Given
        ReviewCreateRequest request = new ReviewCreateRequest(5, "Great Book");
        List<MultipartFile> images = List.of(new MockMultipartFile("img", "test.jpg", "image/jpeg", "data".getBytes()));

        given(orderFeignClient.hasPurchasedBook(anyLong(), anyLong())).willReturn(true);

        given(reviewRepository.existsByBookIdAndMemberId(bookId, memberId)).willReturn(false);
        given(bookRepository.findById(bookId)).willReturn(Optional.of(testBook));
        given(imageUploadService.uploadImage(any())).willReturn("http://minio/url");

        // When
        reviewService.saveReview(request, bookId, memberId, images);

        // Then
        verify(reviewImageRepository).saveAll(anyList()); // 이미지 저장 호출 확인
        verify(reviewRepository).save(any(Review.class)); // 리뷰 저장 호출 확인

        // 이벤트 발행 검증
        ArgumentCaptor<Object> eventCaptor = ArgumentCaptor.forClass(Object.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());

        Object event = eventCaptor.getValue();
        assertThat(event).isInstanceOf(ReviewCreatedEvent.class);
        assertThat(((ReviewCreatedEvent) event).eventType()).isEqualTo("EARN_PHOTO_REVIEW");
    }

    // ============================
    // 2. getReviewList & getCachedReviewPage (조회 & 캐싱)
    // ============================

    @Test
    @DisplayName("리뷰 리스트 조회: 로그인 유저 좋아요 여부(Personalization) 확인")
    void getReviewList_LoginUser_Liked() {
        // Given
        Pageable pageable = PageRequest.of(0, 10);

        // 캐시된 리뷰 페이지 Mocking
        List<ReviewImageResponse> images = List.of();
        BookReviewResponse cachedResponse = new BookReviewResponse(reviewId, memberId, "홍*동", "Content", 5, null, images, 0, false);
        Page<BookReviewResponse> cachedPage = new PageImpl<>(List.of(cachedResponse));

        given(self.getCachedReviewPage(bookId, pageable)).willReturn(cachedPage);

        // 회원이 좋아요 누른 리뷰 ID 목록 Mocking
        given(reviewLikeRepository.findReviewIdsByMemberIdAndReviewIds(eq(memberId), anyList()))
                .willReturn(List.of(reviewId));

        // When
        Page<BookReviewResponse> result = reviewService.getReviewList(bookId, pageable, memberId);

        // Then
        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).isLiked()).isTrue(); // 좋아요가 True로 바뀌었는지 확인
    }

    @Test
    @DisplayName("캐시 메서드(getCachedReviewPage): 닉네임 마스킹 확인")
    void getCachedReviewPage_Masking() {
        // Given
        Pageable pageable = PageRequest.of(0, 10);
        Review r1 = new Review(5, "Content", testBook, 101L);
        ReflectionTestUtils.setField(r1, "reviewImages", new ArrayList<>()); // 이미지 초기화
        Page<Review> reviewPage = new PageImpl<>(List.of(r1));

        given(reviewRepository.findByBookId(bookId, pageable)).willReturn(reviewPage);
        // Feign Mock: 이름이 "홍길동"인 회원
        given(memberFeignClient.getMembersInfo(anyList())).willReturn(List.of(new MemberResponse(101L, "홍길동")));

        // When
        Page<BookReviewResponse> result = reviewService.getCachedReviewPage(bookId, pageable);

        // Then
        assertThat(result.getContent().get(0).loginId()).isEqualTo("홍*동"); // 마스킹 검증
    }

    // ============================
    // 3. updateReview (리뷰 수정)
    // ============================

    @Test
    @DisplayName("수정 성공: 텍스트 수정 + 이미지 추가 (일반->포토 업그레이드 이벤트)")
    void updateReview_Success_Upgrade() {
        // Given
        given(reviewRepository.findById(reviewId)).willReturn(Optional.of(testReview));
        given(imageUploadService.uploadImage(any())).willReturn("new-img-url");
        setupRedisScanMock(); // 캐시 삭제 Mock 설정

        ReviewUpdateRequest request = new ReviewUpdateRequest("Updated Content", 4, null);
        List<MultipartFile> newImages = List.of(new MockMultipartFile("img", "t.jpg", "image/jpeg", "d".getBytes()));

        // When
        reviewService.updateReview(request, bookId, reviewId, memberId, newImages);

        // Then
        assertThat(testReview.getReviewContent()).isEqualTo("Updated Content"); // 내용 변경 확인
        assertThat(testReview.getRating()).isEqualTo(4); // 별점 변경 확인

        // 캐시 삭제 확인
        verify(redisTemplate).delete(anyList());

        // 이벤트 확인 (업그레이드 이벤트 발생 여부)
        ArgumentCaptor<Object> eventCaptor = ArgumentCaptor.forClass(Object.class);
        verify(eventPublisher, atLeastOnce()).publishEvent(eventCaptor.capture());

        boolean hasUpgradeEvent = eventCaptor.getAllValues().stream()
                .anyMatch(e -> e instanceof ReviewCreatedEvent &&
                        ((ReviewCreatedEvent) e).eventType().equals("EARN_REVIEW_UPGRADE"));
        assertThat(hasUpgradeEvent).isTrue();
    }

    @Test
    @DisplayName("수정 실패: 본인 리뷰 아님 (REVIEW_NOT_AUTHOR)")
    void updateReview_Fail_NotAuthor() {
        given(reviewRepository.findById(reviewId)).willReturn(Optional.of(testReview));
        Long otherMemberId = 999L;

        ReviewUpdateRequest request = new ReviewUpdateRequest("C", 5, null);

        assertThatThrownBy(() -> reviewService.updateReview(request, bookId, reviewId, otherMemberId, null))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.REVIEW_NOT_AUTHOR);
    }

    // ============================
    // 4. removeReview (리뷰 삭제)
    // ============================

    @Test
    @DisplayName("삭제 성공: DB 삭제, 캐시 삭제, 이벤트 발행(이미지삭제, 리뷰삭제) 확인")
    void removeReview_Success() {
        // Given
        // 이미지가 있는 리뷰 상황 가정
        ReviewImage img = new ReviewImage(testReview, "http://url.com");
        testReview.getReviewImages().add(img);

        given(reviewRepository.findById(reviewId)).willReturn(Optional.of(testReview));
        setupRedisScanMock(); // 캐시 삭제 Mock

        // When
        reviewService.removeReview(reviewId, memberId);

        // Then
        // 1. DB 삭제 확인
        verify(reviewRepository).delete(testReview);

        // 2. 캐시 삭제 확인
        verify(redisTemplate).delete(anyList());

        // 3. 이벤트 발행 확인 (2가지 이벤트가 모두 발생해야 함)
        ArgumentCaptor<Object> eventCaptor = ArgumentCaptor.forClass(Object.class);
        verify(eventPublisher, times(2)).publishEvent(eventCaptor.capture());
        List<Object> events = eventCaptor.getAllValues();

        // 3-1. 이미지 삭제 이벤트 확인
        boolean hasImageDeleteEvent = events.stream()
                .anyMatch(ReviewImageDeleteEvent.class::isInstance);
        assertThat(hasImageDeleteEvent).isTrue();

        // 3-2. 리뷰 삭제 이벤트(포인트 차감 등) 확인
        boolean hasReviewDeleteEvent = events.stream()
                .anyMatch(ReviewDeletedEvent.class::isInstance);
        assertThat(hasReviewDeleteEvent).isTrue();
    }

    // ============================
    // 5. toggleReviewLike (좋아요)
    // ============================

    @Test
    @DisplayName("좋아요 실패: Redis 락 획득 실패 (광클 방지)")
    void toggleReviewLike_Fail_Locked() {
        given(redisTemplate.opsForValue()).willReturn(valueOperations);
        // Lock 획득 실패(false) 시뮬레이션
        given(valueOperations.setIfAbsent(anyString(), anyString(), any(Duration.class))).willReturn(false);

        assertThatThrownBy(() -> reviewService.toggleReviewLike(reviewId, memberId, bookId))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.TOO_MANY_REQUESTS);
    }

    @Test
    @DisplayName("좋아요 성공: 좋아요 추가")
    void toggleReviewLike_Success_Add() {
        Long likerId = 200L;
        given(redisTemplate.opsForValue()).willReturn(valueOperations);
        given(valueOperations.setIfAbsent(anyString(), anyString(), any(Duration.class))).willReturn(true);
        given(reviewRepository.findById(reviewId)).willReturn(Optional.of(testReview));

        // 기존 좋아요 없음
        given(reviewLikeRepository.findByMemberIdAndReviewId(likerId, reviewId))
                .willReturn(Optional.empty());

        // When
        boolean isLiked = reviewService.toggleReviewLike(reviewId, likerId, bookId);

        // Then
        assertThat(isLiked).isTrue();
        verify(reviewLikeRepository).save(any(ReviewLike.class));
        verify(reviewRepository).increaseLikeCount(reviewId);
        verify(redisTemplate).delete(anyString()); // 락 해제 확인
    }

    // ============================
    // 6. 기타 조회 (MyPage 등)
    // ============================

    @Test
    @DisplayName("마이페이지 리뷰 목록: 삭제된 도서 처리 확인")
    void getMyReviewList_DeletedBook() {
        // Book이 null인 리뷰 생성
        Review deletedBookReview = new Review(5, "Content", null, memberId);
        ReflectionTestUtils.setField(deletedBookReview, "id", 1L);

        Page<Review> page = new PageImpl<>(List.of(deletedBookReview));
        given(reviewRepository.findByMemberId(eq(memberId), any(Pageable.class))).willReturn(page);

        // When
        Page<MyPageReviewResponse> result = reviewService.getMyReviewList(memberId, PageRequest.of(0, 10));

        // Then
        assertThat(result.getContent().get(0).bookTitle()).isEqualTo("삭제된 도서");
    }
}