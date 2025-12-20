package com.nhnacademy.book_server.service.review;

import com.nhnacademy.book_server.dto.ReviewCreatedEvent;
import com.nhnacademy.book_server.dto.ReviewImageDeleteEvent;
import com.nhnacademy.book_server.dto.request.ReviewCreateRequest;
import com.nhnacademy.book_server.dto.request.ReviewUpdateRequest;
import com.nhnacademy.book_server.dto.response.BookReviewResponse;
import com.nhnacademy.book_server.dto.response.MemberResponse;
import com.nhnacademy.book_server.dto.response.MyPageReviewResponse;
import com.nhnacademy.book_server.dto.response.ReviewCreateResponse;
import com.nhnacademy.book_server.dto.response.UpdateReviewResponse;
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
import com.nhnacademy.book_server.service.impl.ReviewServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.multipart.MultipartFile;

import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ReviewServiceImplTest {

    @InjectMocks
    private ReviewServiceImpl reviewService;

    @Mock private ReviewRepository reviewRepository;
    @Mock private ReviewImageRepository reviewImageRepository;
    @Mock private MinioImageService imageUploadService;
    @Mock private ApplicationEventPublisher eventPublisher;
    @Mock private OrderFeignClient orderFeignClient;
    @Mock private MemberFeignClient memberFeignClient;
    @Mock private BookRepository bookRepository;
    @Mock private ReviewLikeRepository reviewLikeRepository;
    @Mock private StringRedisTemplate redisTemplate;
    @Mock private ValueOperations<String, String> valueOperations;

    @Mock private ReviewServiceImpl self; // For self-invocation mocking

    @BeforeEach
    void setUp() {
        // self 주입 (순환 참조 모킹 해결)
        ReflectionTestUtils.setField(reviewService, "self", self);
    }

    @Test
    @DisplayName("리뷰 생성 - 성공 (일반 리뷰)")
    void saveReview_Success() {
        // given
        Long bookId = 1L;
        Long memberId = 1L;
        ReviewCreateRequest request = new ReviewCreateRequest(5, "content");
        Book book = new Book();
        ReflectionTestUtils.setField(book, "id", bookId);

        given(reviewRepository.existsByBookIdAndMemberId(bookId, memberId)).willReturn(false);
        given(bookRepository.findById(bookId)).willReturn(Optional.of(book));
        given(reviewRepository.save(any(Review.class))).willAnswer(invocation -> {
            Review r = invocation.getArgument(0);
            ReflectionTestUtils.setField(r, "id", 100L);
            return r;
        });

        // when
        ReviewCreateResponse response = reviewService.saveReview(request, bookId, memberId, null);

        // then
        assertThat(response.reviewId()).isEqualTo(100L);
        verify(eventPublisher).publishEvent(any(ReviewCreatedEvent.class)); // EARN_REVIEW
    }

    @Test
    @DisplayName("리뷰 생성 - 실패 (이미 작성됨)")
    void saveReview_Fail_Duplicate() {
        // given
        given(reviewRepository.existsByBookIdAndMemberId(1L, 1L)).willReturn(true);

        // when & then
        assertThatThrownBy(() -> reviewService.saveReview(new ReviewCreateRequest(5, "c"), 1L, 1L, null))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.REVIEW_DUP);
    }

    @Test
    @DisplayName("리뷰 생성 - 실패 (이미지 개수 초과)")
    void saveReview_Fail_ImageLimit() {
        // given
        Book book = new Book();
        given(reviewRepository.existsByBookIdAndMemberId(1L, 1L)).willReturn(false);
        given(bookRepository.findById(1L)).willReturn(Optional.of(book));

        List<MultipartFile> images = Collections.nCopies(6, mock(MultipartFile.class));

        // when & then
        assertThatThrownBy(() -> reviewService.saveReview(new ReviewCreateRequest(5, "c"), 1L, 1L, images))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.REVIEW_IMAGE_LIMIT_EXCEEDED);
    }

    @Test
    @DisplayName("리뷰 리스트 조회 - 캐시 및 좋아요 상태 반영")
    void getReviewList() {
        // given
        Long bookId = 1L;
        Long memberId = 100L;
        Pageable pageable = PageRequest.of(0, 10);

        BookReviewResponse cachedResponse = new BookReviewResponse(10L, 200L, "nick", "content", 5, null, List.of(), 0, false);
        Page<BookReviewResponse> cachedPage = new PageImpl<>(List.of(cachedResponse));

        // self 호출 모킹 (캐시된 데이터 반환 시뮬레이션)
        given(self.getCachedReviewPage(bookId, pageable)).willReturn(cachedPage);
        // 사용자가 좋아요 누른 리뷰 조회
        given(reviewLikeRepository.findReviewIdsByMemberIdAndReviewIds(eq(memberId), anyList()))
                .willReturn(List.of(10L));

        // when
        Page<BookReviewResponse> result = reviewService.getReviewList(bookId, pageable, memberId);

        // then
        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).isLiked()).isTrue(); // 좋아요 상태가 true로 변경되었는지 확인
    }

    @Test
    @DisplayName("캐시 메서드 테스트 (getCachedReviewPage) - 닉네임 마스킹 확인")
    void getCachedReviewPage() {
        // given
        Long bookId = 1L;
        Pageable pageable = PageRequest.of(0, 10);
        Review review = new Review(5, "content", new Book(), 200L);
        ReflectionTestUtils.setField(review, "id", 10L);

        // Member Feign 응답
        MemberResponse memberResponse = new MemberResponse(200L, "홍길동");

        given(reviewRepository.findByBookId(bookId, pageable)).willReturn(new PageImpl<>(List.of(review)));
        given(memberFeignClient.getMembersInfo(anyList())).willReturn(List.of(memberResponse));

        // when
        // self가 아닌 실제 메서드 호출 테스트 필요하므로 직접 호출하거나 Spy 사용이 이상적이나, 여기서는 로직 검증을 위해 reviewService 메서드 직접 호출 (self 의존성 제거 후 테스트하거나, 실제 호출 시 self가 호출되도록 설정 필요하지만 단위테스트에서는 직접 로직 확인)
        // *주의*: @InjectMocks로 주입된 self는 Mock 객체이므로, 이 테스트를 위해서는 self.getCachedReviewPage가 아니라 내부 로직을 수행하는 메서드를 호출해야 함.
        // 하지만 getCachedReviewPage는 public이므로 직접 호출 가능. 단, 내부의 self 호출이 없으므로 안전.
        Page<BookReviewResponse> result = reviewService.getCachedReviewPage(bookId, pageable);

        // then
        assertThat(result.getContent().get(0).loginId()).isEqualTo("홍*동"); // 마스킹 확인
    }

//    @Test
//    @DisplayName("내 리뷰 단건 조회")
//    void getMyReview() {
//        // given
//        Long bookId = 1L;
//        Long memberId = 100L;
//        Review review = new Review(5, "content", new Book(), memberId);
//        ReflectionTestUtils.setField(review, "id", 10L);
//
//        given(memberFeignClient.getMembersInfo(any())).willReturn(List.of(new MemberResponse(memberId, "tester")));
//        given(memberFeignClient.getMembersInfo(eq(List.of(memberId)))).willReturn(List.of(new MemberResponse(memberId, "tester")));
//
//        // when
//        BookReviewResponse response = reviewService.getMyReview(bookId, memberId);
//
//        // then
//        assertThat(response).isNotNull();
//        assertThat(response.loginId()).isEqualTo("tester");
//    }

    @Test
    @DisplayName("내 리뷰 리스트 조회 (마이페이지)")
    void getMyReviewList() {
        // given
        Long memberId = 100L;
        Book book = new Book();
        ReflectionTestUtils.setField(book, "id", 1L);
        ReflectionTestUtils.setField(book, "title", "Test Book");

        Review review = new Review(5, "c", book, memberId);
        ReflectionTestUtils.setField(review, "id", 10L);

        given(reviewRepository.findByMemberId(eq(memberId), any(Pageable.class)))
                .willReturn(new PageImpl<>(List.of(review)));

        // when
        Page<MyPageReviewResponse> result = reviewService.getMyReviewList(memberId, PageRequest.of(0, 10));

        // then
        assertThat(result.getContent().get(0).bookTitle()).isEqualTo("Test Book");
    }

    @Test
    @DisplayName("리뷰 수정 - 성공 (이미지 삭제 및 추가)")
    void updateReview_Success() {
        // given
        Long bookId = 1L;
        Long reviewId = 10L;
        Long memberId = 100L;
        Book book = new Book();
        ReflectionTestUtils.setField(book, "id", bookId);

        Review review = new Review(5, "old", book, memberId);
        ReflectionTestUtils.setField(review, "id", reviewId);

        ReviewImage oldImage = new ReviewImage(review, "old-url");
        ReflectionTestUtils.setField(oldImage, "id", 50L);
        review.getReviewImages().add(oldImage);

        ReviewUpdateRequest request = new ReviewUpdateRequest("new", 4, List.of(50L));
        List<MultipartFile> newImages = List.of(mock(MultipartFile.class));

        given(reviewRepository.findById(reviewId)).willReturn(Optional.of(review));
        given(reviewImageRepository.findAllById(List.of(50L))).willReturn(new java.util.ArrayList<>(List.of(oldImage)));
        given(imageUploadService.uploadImage(any())).willReturn("new-url");

        // when
        UpdateReviewResponse response = reviewService.updateReview(request, bookId, reviewId, memberId, newImages);

        // then
        assertThat(response.content()).isEqualTo("new");
        assertThat(review.getReviewImages()).hasSize(1); // 1개 삭제, 1개 추가 -> 1개
        verify(eventPublisher).publishEvent(any(ReviewImageDeleteEvent.class));
    }

    @Test
    @DisplayName("리뷰 좋아요 토글 - 좋아요 추가")
    void toggleReviewLike_Add() {
        // given
        Long reviewId = 10L;
        Long memberId = 100L;
        Long bookId = 1L;

        Review review = new Review(5, "c", new Book(), 200L); // 작성자 다름
        given(redisTemplate.opsForValue()).willReturn(valueOperations);
        given(valueOperations.setIfAbsent(anyString(), anyString(), any(Duration.class))).willReturn(true);
        given(reviewRepository.findById(reviewId)).willReturn(Optional.of(review));
        given(reviewLikeRepository.findByMemberIdAndReviewId(memberId, reviewId)).willReturn(Optional.empty());

        // when
        boolean result = reviewService.toggleReviewLike(reviewId, memberId, bookId);

        // then
        assertThat(result).isTrue();
        verify(reviewLikeRepository).save(any(ReviewLike.class));
        verify(reviewRepository).increaseLikeCount(reviewId);
    }

    @Test
    @DisplayName("리뷰 좋아요 토글 - 광클 방지 (Lock 실패)")
    void toggleReviewLike_Locked() {
        // given
        Long reviewId = 10L;
        Long memberId = 100L;
        given(redisTemplate.opsForValue()).willReturn(valueOperations);
        given(valueOperations.setIfAbsent(anyString(), anyString(), any(Duration.class))).willReturn(false);

        // when & then
        assertThatThrownBy(() -> reviewService.toggleReviewLike(reviewId, memberId, 1L))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.TOO_MANY_REQUESTS);
    }

    @Test
    @DisplayName("리뷰 삭제")
    void removeReview() {
        // given
        Long reviewId = 1L;
        Review review = new Review();
        ReviewImage image = new ReviewImage(review, "url");
        review.getReviewImages().add(image);

        given(reviewRepository.findById(reviewId)).willReturn(Optional.of(review));

        // when
        reviewService.removeReview(reviewId);

        // then
        verify(imageUploadService).deleteImages(anyList());
        verify(reviewRepository).delete(review);
    }
}