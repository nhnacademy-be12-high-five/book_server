package com.nhnacademy.book_server.service.review;

import com.nhnacademy.book_server.dto.event.ReviewCreatedEvent;
import com.nhnacademy.book_server.dto.request.ReviewCreateRequest;
import com.nhnacademy.book_server.dto.request.ReviewUpdateRequest;
import com.nhnacademy.book_server.dto.response.BookReviewResponse;
import com.nhnacademy.book_server.dto.response.MemberResponse;
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
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.multipart.MultipartFile;

import java.time.Duration;
import java.util.*;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ReviewServiceImplTest {

    @InjectMocks
    private ReviewServiceImpl reviewService;

    @Mock private ReviewServiceImpl self;
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
    @Mock private Cursor<String> cursor;

    private final Long BOOK_ID = 1L;
    private final Long MEMBER_ID = 100L;
    private final Long REVIEW_ID = 10L;
    private Book testBook;
    private Review testReview;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(reviewService, "self", self);
        lenient().when(orderFeignClient.hasPurchasedBook(anyLong(), anyLong()))
                .thenReturn(true);
        testBook = Book.builder().id(BOOK_ID).title("Test Book").build();
        testReview = new Review(5, "Content", testBook, MEMBER_ID);
        ReflectionTestUtils.setField(testReview, "id", REVIEW_ID);
        ReflectionTestUtils.setField(testReview, "reviewImages", new ArrayList<>());
    }

    private void setupRedisScanMock() {
        lenient().when(redisTemplate.scan(any())).thenReturn(cursor);
        lenient().doAnswer(invocation -> {
            Consumer<String> consumer = invocation.getArgument(0);
            consumer.accept("bookReviews::1_0");
            return null;
        }).when(cursor).forEachRemaining(any(Consumer.class));
    }

    // ============================
    // 1. saveReview (리뷰 작성)
    // ============================

    @Test
    @DisplayName("작성 실패: 이미 작성한 리뷰가 존재함 (REVIEW_DUP)")
    void saveReview_Fail_Duplicate() {
        given(reviewRepository.existsByBookIdAndMemberId(BOOK_ID, MEMBER_ID)).willReturn(true);

        ReviewCreateRequest request = new ReviewCreateRequest(5, "Content");

        assertThatThrownBy(() -> reviewService.saveReview(request, BOOK_ID, MEMBER_ID, null))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.REVIEW_DUP);
    }

    @Test
    @DisplayName("작성 실패: 존재하지 않는 책 (BOOK_NOT_FOUND)")
    void saveReview_Fail_BookNotFound() {
        given(reviewRepository.existsByBookIdAndMemberId(BOOK_ID, MEMBER_ID)).willReturn(false);
        given(bookRepository.findById(BOOK_ID)).willReturn(Optional.empty());

        ReviewCreateRequest request = new ReviewCreateRequest(5, "Content");

        assertThatThrownBy(() -> reviewService.saveReview(request, BOOK_ID, MEMBER_ID, null))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.BOOK_NOT_FOUND);
    }

    @Test
    @DisplayName("작성 실패: 이미지 개수 초과 (REVIEW_IMAGE_LIMIT_EXCEEDED)")
    void saveReview_Fail_ImageLimit() {
        given(reviewRepository.existsByBookIdAndMemberId(BOOK_ID, MEMBER_ID)).willReturn(false);
        given(bookRepository.findById(BOOK_ID)).willReturn(Optional.of(testBook));

        // 6개의 이미지 생성
        List<MultipartFile> images = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            images.add(new MockMultipartFile("img", "test.jpg", "image/jpeg", "data".getBytes()));
        }
        ReviewCreateRequest request = new ReviewCreateRequest(5, "Content");

        assertThatThrownBy(() -> reviewService.saveReview(request, BOOK_ID, MEMBER_ID, images))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.REVIEW_IMAGE_LIMIT_EXCEEDED);
    }

    @Test
    @DisplayName("작성 성공: 이미지 없음 (EARN_REVIEW 이벤트 발생)")
    void saveReview_Success_NoImage() {
        // given
        ReviewCreateRequest request = new ReviewCreateRequest(5, "Content");
        given(reviewRepository.existsByBookIdAndMemberId(BOOK_ID, MEMBER_ID)).willReturn(false);
        given(bookRepository.findById(BOOK_ID)).willReturn(Optional.of(testBook));
        setupRedisScanMock();

        // when
        reviewService.saveReview(request, BOOK_ID, MEMBER_ID, null);

        // then
        verify(reviewRepository).save(any(Review.class));

        // [수정] ArgumentCaptor<Object> 사용
        ArgumentCaptor<Object> eventCaptor = ArgumentCaptor.forClass(Object.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());

        Object event = eventCaptor.getValue();
        assertThat(event).isInstanceOf(ReviewCreatedEvent.class);
        assertThat(((ReviewCreatedEvent) event).eventType()).isEqualTo("EARN_REVIEW");
    }

    @Test
    @DisplayName("작성 성공: 이미지 포함 (EARN_PHOTO_REVIEW 이벤트 발생)")
    void saveReview_Success_WithImage() {
        // given
        ReviewCreateRequest request = new ReviewCreateRequest(5, "Content");
        List<MultipartFile> images = List.of(new MockMultipartFile("img", "test.jpg", "image/jpeg", "data".getBytes()));

        given(reviewRepository.existsByBookIdAndMemberId(BOOK_ID, MEMBER_ID)).willReturn(false);
        given(bookRepository.findById(BOOK_ID)).willReturn(Optional.of(testBook));
        given(imageUploadService.uploadImage(any())).willReturn("url");
        setupRedisScanMock();

        // when
        reviewService.saveReview(request, BOOK_ID, MEMBER_ID, images);

        // then
        verify(reviewImageRepository).saveAll(anyList());

        // [수정] ArgumentCaptor<Object> 사용
        ArgumentCaptor<Object> eventCaptor = ArgumentCaptor.forClass(Object.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());

        Object event = eventCaptor.getValue();
        assertThat(event).isInstanceOf(ReviewCreatedEvent.class);
        assertThat(((ReviewCreatedEvent) event).eventType()).isEqualTo("EARN_PHOTO_REVIEW");
    }

    // ============================
    // 2. getReviewList & getCachedReviewPage
    // ============================

    @Test
    @DisplayName("리뷰 리스트 조회: 비로그인 유저 (캐시된 페이지만 반환)")
    void getReviewList_Guest() {
        Pageable pageable = PageRequest.of(0, 10);
        Page<BookReviewResponse> cachedPage = new PageImpl<>(List.of());
        given(self.getCachedReviewPage(BOOK_ID, pageable)).willReturn(cachedPage);

        Page<BookReviewResponse> result = reviewService.getReviewList(BOOK_ID, pageable, null);

        assertThat(result).isEmpty();
        verify(reviewLikeRepository, never()).findReviewIdsByMemberIdAndReviewIds(any(), any());
    }

    @Test
    @DisplayName("캐시 메서드 테스트: 닉네임 마스킹 및 Feign 예외 처리")
    void getCachedReviewPage_FeignError() {
        Pageable pageable = PageRequest.of(0, 10);
        Review r1 = new Review(5, "Content", testBook, 101L);
        ReflectionTestUtils.setField(r1, "reviewImages", new ArrayList<>());
        Page<Review> reviewPage = new PageImpl<>(List.of(r1));

        given(reviewRepository.findByBookId(BOOK_ID, pageable)).willReturn(reviewPage);
        given(memberFeignClient.getMembersInfo(anyList())).willThrow(new RuntimeException("Feign Error"));

        Page<BookReviewResponse> result = reviewService.getCachedReviewPage(BOOK_ID, pageable);

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).loginId()).startsWith("알");
    }

    @Test
    @DisplayName("캐시 메서드 테스트: 닉네임 정상 조회 및 마스킹")
    void getCachedReviewPage_Success() {
        Pageable pageable = PageRequest.of(0, 10);
        Review r1 = new Review(5, "Content", testBook, 101L);
        ReflectionTestUtils.setField(r1, "reviewImages", new ArrayList<>());
        Page<Review> reviewPage = new PageImpl<>(List.of(r1));

        given(reviewRepository.findByBookId(BOOK_ID, pageable)).willReturn(reviewPage);
        given(memberFeignClient.getMembersInfo(anyList())).willReturn(List.of(
                new MemberResponse(101L, "홍길동")
        ));

        Page<BookReviewResponse> result = reviewService.getCachedReviewPage(BOOK_ID, pageable);

        assertThat(result.getContent().get(0).loginId()).isEqualTo("홍*동");
    }

    // ============================
    // 3. updateReview (리뷰 수정)
    // ============================

    @Test
    @DisplayName("수정 실패: 본인 리뷰가 아님 (REVIEW_NOT_AUTHOR)")
    void updateReview_Fail_NotAuthor() {
        given(reviewRepository.findById(REVIEW_ID)).willReturn(Optional.of(testReview));

        ReviewUpdateRequest request = new ReviewUpdateRequest("New Content", 5, null);
        Long otherMemberId = 999L;

        assertThatThrownBy(() -> reviewService.updateReview(request, BOOK_ID, REVIEW_ID, otherMemberId, null))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.REVIEW_NOT_AUTHOR);
    }

    @Test
    @DisplayName("수정 실패: 책 ID 불일치 (REVIEW_NOT_MATCH_BOOK)")
    void updateReview_Fail_MatchBook() {
        given(reviewRepository.findById(REVIEW_ID)).willReturn(Optional.of(testReview));

        ReviewUpdateRequest request = new ReviewUpdateRequest("New Content", 5, null);
        Long otherBookId = 555L;

        assertThatThrownBy(() -> reviewService.updateReview(request, otherBookId, REVIEW_ID, MEMBER_ID, null))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.REVIEW_NOT_MATCH_BOOK);
    }

    @Test
    @DisplayName("수정 실패: 이미지 개수 초과 (기존-삭제+추가 > 5)")
    void updateReview_Fail_ImageLimit() {
        for(int i=0; i<3; i++) testReview.getReviewImages().add(new ReviewImage(testReview, "url"));

        given(reviewRepository.findById(REVIEW_ID)).willReturn(Optional.of(testReview));

        List<MultipartFile> newImages = new ArrayList<>();
        for(int i=0; i<3; i++) newImages.add(new MockMultipartFile("img", "t.jpg", "image/jpeg", "d".getBytes()));

        ReviewUpdateRequest request = new ReviewUpdateRequest("C", 5, null);

        assertThatThrownBy(() -> reviewService.updateReview(request, BOOK_ID, REVIEW_ID, MEMBER_ID, newImages))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.REVIEW_IMAGE_LIMIT_EXCEEDED);
    }

    @Test
    @DisplayName("수정 성공: 텍스트 변경 및 이미지 업그레이드 이벤트")
    void updateReview_Success_Upgrade() {
        // given
        given(reviewRepository.findById(REVIEW_ID)).willReturn(Optional.of(testReview));
        given(imageUploadService.uploadImage(any())).willReturn("url");
        setupRedisScanMock();

        List<MultipartFile> newImages = List.of(new MockMultipartFile("img", "t.jpg", "image/jpeg", "d".getBytes()));
        ReviewUpdateRequest request = new ReviewUpdateRequest("New Content", 5, null);

        // when
        reviewService.updateReview(request, BOOK_ID, REVIEW_ID, MEMBER_ID, newImages);

        // then
        assertThat(testReview.getReviewContent()).isEqualTo("New Content");

        // [수정] ArgumentCaptor<Object> 사용
        ArgumentCaptor<Object> eventCaptor = ArgumentCaptor.forClass(Object.class);
        // 이벤트가 여러 번 발생할 수 있으니 atLeastOnce() 사용 가능하지만, 여기선 업그레이드 이벤트 1회 확인
        verify(eventPublisher, atLeastOnce()).publishEvent(eventCaptor.capture());

        // 캡처된 이벤트 중 ReviewCreatedEvent 찾기
        Optional<Object> upgradeEvent = eventCaptor.getAllValues().stream()
                .filter(e -> e instanceof ReviewCreatedEvent)
                .findFirst();

        assertThat(upgradeEvent).isPresent();
        assertThat(((ReviewCreatedEvent) upgradeEvent.get()).eventType()).isEqualTo("EARN_REVIEW_UPGRADE");
    }

    // ============================
    // 4. toggleReviewLike (좋아요)
    // ============================

    @Test
    @DisplayName("좋아요 실패: Redis 락 획득 실패 (광클)")
    void toggleReviewLike_Fail_Locked() {
        given(redisTemplate.opsForValue()).willReturn(valueOperations);
        given(valueOperations.setIfAbsent(anyString(), anyString(), any(Duration.class))).willReturn(false);

        assertThatThrownBy(() -> reviewService.toggleReviewLike(REVIEW_ID, MEMBER_ID, BOOK_ID))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.TOO_MANY_REQUESTS);
    }

    @Test
    @DisplayName("좋아요 실패: 본인 리뷰에 좋아요 시도")
    void toggleReviewLike_Fail_SelfLike() {
        given(redisTemplate.opsForValue()).willReturn(valueOperations);
        given(valueOperations.setIfAbsent(anyString(), anyString(), any(Duration.class))).willReturn(true);
        given(reviewRepository.findById(REVIEW_ID)).willReturn(Optional.of(testReview));

        boolean result = reviewService.toggleReviewLike(REVIEW_ID, MEMBER_ID, BOOK_ID);

        assertThat(result).isFalse();
        verify(reviewLikeRepository, never()).save(any());
        verify(redisTemplate).delete(contains("like_lock"));
    }

    @Test
    @DisplayName("좋아요 취소: 이미 좋아요가 존재함")
    void toggleReviewLike_Cancel() {
        given(redisTemplate.opsForValue()).willReturn(valueOperations);
        given(valueOperations.setIfAbsent(anyString(), anyString(), any(Duration.class))).willReturn(true);

        Review otherReview = new Review(5, "C", testBook, 999L);
        ReflectionTestUtils.setField(otherReview, "id", REVIEW_ID);
        given(reviewRepository.findById(REVIEW_ID)).willReturn(Optional.of(otherReview));

        ReviewLike existingLike = new ReviewLike(otherReview, MEMBER_ID);
        given(reviewLikeRepository.findByMemberIdAndReviewId(MEMBER_ID, REVIEW_ID)).willReturn(Optional.of(existingLike));

        boolean result = reviewService.toggleReviewLike(REVIEW_ID, MEMBER_ID, BOOK_ID);

        assertThat(result).isFalse();
        verify(reviewLikeRepository).delete(existingLike);
        verify(reviewRepository).decreaseLikeCount(REVIEW_ID);
    }

    // ============================
    // 5. 기타 메서드 (getMyReview, getMyReviewList)
    // ============================

    @Test
    @DisplayName("내 리뷰 단건 조회: 존재하지 않을 경우 null")
    void getMyReview_Null() {
        given(reviewRepository.findByMemberIdAndBookId(MEMBER_ID, BOOK_ID)).willReturn(null);

        BookReviewResponse response = reviewService.getMyReview(BOOK_ID, MEMBER_ID);

        assertThat(response).isNull();
    }

    @Test
    @DisplayName("내 리뷰 단건 조회: 존재할 경우 DTO 반환")
    void getMyReview_Success() {
        given(reviewRepository.findByMemberIdAndBookId(MEMBER_ID, BOOK_ID)).willReturn(testReview);

        BookReviewResponse response = reviewService.getMyReview(BOOK_ID, MEMBER_ID);

        assertThat(response).isNotNull();
        assertThat(response.content()).isEqualTo("Content");
    }

    @Test
    @DisplayName("마이페이지 리뷰 리스트 조회: Book이 null인 경우 (삭제된 도서 처리)")
    void getMyReviewList_DeletedBook() {
        Review r = new Review(5, "C", null, MEMBER_ID);
        ReflectionTestUtils.setField(r, "id", 1L);
        Page<Review> p = new PageImpl<>(List.of(r));

        given(reviewRepository.findByMemberId(eq(MEMBER_ID), any(Pageable.class))).willReturn(p);

        Page<com.nhnacademy.book_server.dto.response.MyPageReviewResponse> result =
                reviewService.getMyReviewList(MEMBER_ID, PageRequest.of(0, 10));

        assertThat(result.getContent().get(0).bookTitle()).isEqualTo("삭제된 도서");
    }

}