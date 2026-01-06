package com.nhnacademy.book_server.listener;

import com.nhnacademy.book_server.config.RabbitMqConfig;
import com.nhnacademy.book_server.dto.event.ReviewCreatedEvent;
import com.nhnacademy.book_server.dto.event.ReviewDeletedEvent;
import com.nhnacademy.book_server.dto.event.ReviewImageDeleteEvent;
import com.nhnacademy.book_server.dto.request.PointEarnRequest;
import com.nhnacademy.book_server.entity.Book;
import com.nhnacademy.book_server.entity.BookReviewAi;
import com.nhnacademy.book_server.repository.BookRepository;
import com.nhnacademy.book_server.repository.BookReviewAiRepository;
import com.nhnacademy.book_server.repository.review.ReviewRepository;
import com.nhnacademy.book_server.service.MinioImageService;
import com.nhnacademy.book_server.service.search.ElasticService;
import com.nhnacademy.book_server.service.search.GeminiTextClientService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;

import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ReviewEventListenerTest {

    @InjectMocks
    private ReviewEventListener reviewEventListener;

    @Mock private RabbitTemplate rabbitTemplate;
    @Mock private BookRepository bookRepository;
    @Mock private ReviewRepository reviewRepository;
    @Mock private BookReviewAiRepository bookAiSummaryRepository;
    @Mock private GeminiTextClientService geminiService;
    @Mock private CacheManager cacheManager;
    @Mock private MinioImageService imageUploadService;
    @Mock private ElasticService elasticService;
    @Mock private Cache cache;

    @Test
    @DisplayName("기본 리뷰 이벤트 처리 (포인트, 통계, ES 카운트 증가)")
    void handleReviewCreated_Success() {
        // Given
        Long memberId = 100L;
        Long bookId = 1L;
        String eventType = "EARN_REVIEW";
        ReviewCreatedEvent event = new ReviewCreatedEvent(memberId, bookId, eventType);

        given(cacheManager.getCache("bookDetail")).willReturn(cache);

        reviewEventListener.handleReviewCreated(event);

        verify(rabbitTemplate).convertAndSend(
                eq(RabbitMqConfig.POINT_EXCHANGE),
                eq(RabbitMqConfig.ROUTING_KEY),
                any(PointEarnRequest.class)
        );

        verify(bookRepository).updateBookReviewStats(bookId);

        verify(elasticService).increaseReviewCount(bookId);

        verify(cache).evict(bookId);
    }

    @Test
    @DisplayName("AI 요약 트리거 - 최초 생성")
    void handleAiSummaryTrigger_Create() {
        Long bookId = 1L;
        ReviewCreatedEvent event = new ReviewCreatedEvent(100L, bookId, "EARN_REVIEW");
        Book book = Book.builder().id(bookId).title("Title").build();

        given(bookRepository.findById(bookId)).willReturn(Optional.of(book));
        given(reviewRepository.countByBookId(bookId)).willReturn(5L); // Threshold
        given(reviewRepository.getAverageRating(bookId)).willReturn(4.0);
        given(bookAiSummaryRepository.findByBook_Id(bookId)).willReturn(Optional.empty());
        given(geminiService.getReviewSummary(any(), any())).willReturn("Summary");

        // Cache Mocking 중요
        given(cacheManager.getCache("bookDetail")).willReturn(cache);

        reviewEventListener.handleAiSummaryTrigger(event);

        verify(bookAiSummaryRepository).save(any(BookReviewAi.class));
        verify(cache).evict(bookId);
    }

    @Test
    @DisplayName("S3 이미지 삭제 이벤트")
    void handleImageDeletion() {
        ReviewImageDeleteEvent event = new ReviewImageDeleteEvent(List.of("url1", "url2"));
        reviewEventListener.handleImageDeletion(event);
        verify(imageUploadService).deleteReviewImages(anyList());
    }

    @Test
    @DisplayName("리뷰 삭제 이벤트 처리 (통계 갱신, ES 카운트 감소, 캐시 초기화)")
    void handleReviewDeleted_Success() {
        // Given
        Long memberId = 100L;
        Long bookId = 1L;
        ReviewDeletedEvent event = new ReviewDeletedEvent(memberId, bookId);

        given(cacheManager.getCache("bookDetail")).willReturn(cache);

        // When
        reviewEventListener.handleReviewDeleted(event);

        // Then
        verify(bookRepository).updateBookReviewStats(bookId); // 통계 재계산 확인
        verify(elasticService).decreaseReviewCount(bookId);   // ES 카운트 감소 확인
        verify(cache).evict(bookId);                          // 캐시 삭제 확인
    }

    @Test
    @DisplayName("AI 요약 트리거 - 기존 요약 갱신 (리뷰 수 10개 이상 증가)")
    void handleAiSummaryTrigger_Update_ByCountDiff() {
        // Given
        Long bookId = 1L;
        ReviewCreatedEvent event = new ReviewCreatedEvent(100L, bookId, "EARN_REVIEW");
        Book book = Book.builder().id(bookId).title("Title").build();

        // 기존 요약 정보 (이전 리뷰 수: 10개)
        BookReviewAi existingSummary = new BookReviewAi(book, "Old Summary", 10L, 4.0);

        given(bookRepository.findById(bookId)).willReturn(Optional.of(book));
        // 현재 리뷰 수: 20개 (차이 10개 >= Threshold 10)
        given(reviewRepository.countByBookId(bookId)).willReturn(20L);
        given(reviewRepository.getAverageRating(bookId)).willReturn(4.0);
        given(bookAiSummaryRepository.findByBook_Id(bookId)).willReturn(Optional.of(existingSummary));

        given(geminiService.getReviewSummary(any(), any())).willReturn("New Summary");
        given(cacheManager.getCache("bookDetail")).willReturn(cache);

        // When
        reviewEventListener.handleAiSummaryTrigger(event);

        // Then
        // save가 호출되었는지 확인 (JPA 변경 감지로 처리될 수도 있지만, 코드상 save를 명시적으로 호출하고 있음)
        verify(bookAiSummaryRepository).save(existingSummary);
        verify(cache).evict(bookId);
    }

    @Test
    @DisplayName("AI 요약 트리거 - 기존 요약 갱신 (평점 0.5점 이상 변동)")
    void handleAiSummaryTrigger_Update_ByRatingDiff() {
        // Given
        Long bookId = 1L;
        ReviewCreatedEvent event = new ReviewCreatedEvent(100L, bookId, "EARN_REVIEW");
        Book book = Book.builder().id(bookId).title("Title").build();

        // 기존 요약 정보 (이전 평점: 4.0)
        BookReviewAi existingSummary = new BookReviewAi(book, "Old Summary", 10L, 4.0);

        given(bookRepository.findById(bookId)).willReturn(Optional.of(book));
        // 현재 평점: 3.5 (차이 0.5 >= Threshold 0.5)
        given(reviewRepository.countByBookId(bookId)).willReturn(12L); // 리뷰 수 차이는 2개로 조건 미달
        given(reviewRepository.getAverageRating(bookId)).willReturn(3.5);
        given(bookAiSummaryRepository.findByBook_Id(bookId)).willReturn(Optional.of(existingSummary));

        given(geminiService.getReviewSummary(any(), any())).willReturn("New Summary");
        given(cacheManager.getCache("bookDetail")).willReturn(cache);

        // When
        reviewEventListener.handleAiSummaryTrigger(event);

        // Then
        verify(bookAiSummaryRepository).save(existingSummary);
    }

    @Test
    @DisplayName("AI 요약 트리거 미발동 - 최초 생성 조건 미달 (리뷰 수 5개 미만)")
    void handleAiSummaryTrigger_NoTrigger_FirstTime() {
        // Given
        Long bookId = 1L;
        ReviewCreatedEvent event = new ReviewCreatedEvent(100L, bookId, "EARN_REVIEW");
        Book book = Book.builder().id(bookId).title("Title").build();

        given(bookRepository.findById(bookId)).willReturn(Optional.of(book));
        // 리뷰 수 4개 (Threshold 5 미만)
        given(reviewRepository.countByBookId(bookId)).willReturn(4L);
        given(reviewRepository.getAverageRating(bookId)).willReturn(4.0);
        given(bookAiSummaryRepository.findByBook_Id(bookId)).willReturn(Optional.empty());

        // When
        reviewEventListener.handleAiSummaryTrigger(event);

        // Then
        // 요약 서비스가 호출되지 않아야 함
        verify(geminiService, never()).getReviewSummary(any(), any());
        verify(bookAiSummaryRepository, never()).save(any());
    }

    @Test
    @DisplayName("AI 요약 트리거 미발동 - 갱신 조건 미달")
    void handleAiSummaryTrigger_NoTrigger_Update() {
        // Given
        Long bookId = 1L;
        ReviewCreatedEvent event = new ReviewCreatedEvent(100L, bookId, "EARN_REVIEW");
        Book book = Book.builder().id(bookId).title("Title").build();

        BookReviewAi existingSummary = new BookReviewAi(book, "Old Summary", 10L, 4.0);

        given(bookRepository.findById(bookId)).willReturn(Optional.of(book));
        // 리뷰 수 차이 5 (< 10), 평점 차이 0.1 (< 0.5)
        given(reviewRepository.countByBookId(bookId)).willReturn(15L);
        given(reviewRepository.getAverageRating(bookId)).willReturn(4.1);
        given(bookAiSummaryRepository.findByBook_Id(bookId)).willReturn(Optional.of(existingSummary));

        // When
        reviewEventListener.handleAiSummaryTrigger(event);

        // Then
        verify(geminiService, never()).getReviewSummary(any(), any());
    }

    @Test
    @DisplayName("S3 이미지 삭제 이벤트 - 이미지 목록이 없으면 실행하지 않음")
    void handleImageDeletion_EmptyList() {
        // Given
        ReviewImageDeleteEvent event = new ReviewImageDeleteEvent(null); // 혹은 Collections.emptyList()

        // When
        reviewEventListener.handleImageDeletion(event);

        // Then
        verify(imageUploadService, never()).deleteReviewImages(any());
    }

    @Test
    @DisplayName("리뷰 생성 - 포인트 전송이 실패해도 통계 업데이트와 캐시 삭제는 수행되어야 함")
    void handleReviewCreated_Fail_PointMessage() {
        // Given
        Long memberId = 100L;
        Long bookId = 1L;
        ReviewCreatedEvent event = new ReviewCreatedEvent(memberId, bookId, "EARN_REVIEW");

        // 포인트 전송 시 예외 발생 설정
        doThrow(new RuntimeException("RabbitMQ Error"))
                .when(rabbitTemplate).convertAndSend(anyString(), anyString(), any(PointEarnRequest.class));

        given(cacheManager.getCache("bookDetail")).willReturn(cache);

        // When
        // 예외가 던져지지 않아야 성공 (내부 catch에서 처리됨)
        reviewEventListener.handleReviewCreated(event);

        // Then
        // 1. 포인트 전송은 시도했어야 함
        verify(rabbitTemplate).convertAndSend(anyString(), anyString(), any(PointEarnRequest.class));

        // 2. 포인트 전송이 실패했더라도, 통계 업데이트는 실행되어야 함 (중요)
        verify(bookRepository).updateBookReviewStats(bookId);

        // 3. 캐시 삭제도 실행되어야 함
        verify(cache).evict(bookId);
    }

    @Test
    @DisplayName("리뷰 생성 - 통계 업데이트가 실패해도 캐시 삭제 등 나머지 로직은 수행되어야 함")
    void handleReviewCreated_Fail_StatsUpdate() {
        // Given
        Long memberId = 100L;
        Long bookId = 1L;
        ReviewCreatedEvent event = new ReviewCreatedEvent(memberId, bookId, "EARN_REVIEW");

        given(cacheManager.getCache("bookDetail")).willReturn(cache);

        // 통계 업데이트 시 예외 발생 설정
        doThrow(new RuntimeException("DB Error")).when(bookRepository).updateBookReviewStats(bookId);

        // When
        reviewEventListener.handleReviewCreated(event);

        // Then
        // 통계 업데이트 실패 로그가 찍혔을 것이고, 메서드는 정상 종료되어야 함
        verify(bookRepository).updateBookReviewStats(bookId);

        // 뒷단의 캐시 삭제는 여전히 실행되어야 함
        verify(cache).evict(bookId);
    }

    @Test
    @DisplayName("AI 요약 트리거 - AI 서비스 호출 중 예외 발생 시 안전하게 종료")
    void handleAiSummaryTrigger_Fail_GeminiService() {
        // Given
        Long bookId = 1L;
        ReviewCreatedEvent event = new ReviewCreatedEvent(100L, bookId, "EARN_REVIEW");
        Book book = Book.builder().id(bookId).title("Title").build();

        given(bookRepository.findById(bookId)).willReturn(Optional.of(book));
        given(reviewRepository.countByBookId(bookId)).willReturn(10L); // Trigger 조건 충족
        given(bookAiSummaryRepository.findByBook_Id(bookId)).willReturn(Optional.empty());

        // AI 서비스가 예외를 던지도록 설정
        given(geminiService.getReviewSummary(any(), any()))
                .willThrow(new RuntimeException("Gemini API Error"));

        // When
        reviewEventListener.handleAiSummaryTrigger(event);

        // Then
        // AI 서비스는 호출되었으나
        verify(geminiService).getReviewSummary(any(), any());

        // 저장 로직은 실행되지 않아야 함 (예외 발생 후 catch로 빠짐)
        verify(bookAiSummaryRepository, never()).save(any());
        // 캐시 삭제도 실행되지 않음 (try 블록 내 뒷부분이므로)
        verify(cacheManager, never()).getCache(anyString());
    }

    @Test
    @DisplayName("이미지 삭제 - S3 서비스 예외 발생 시 안전하게 처리")
    void handleImageDeletion_Fail_S3() {
        // Given
        ReviewImageDeleteEvent event = new ReviewImageDeleteEvent(List.of("url1", "url2"));

        // 이미지 서비스에서 예외 발생
        doThrow(new RuntimeException("S3 Error")).when(imageUploadService).deleteReviewImages(anyList());

        // When & Then
        // 예외가 전파되지 않고 로그만 남기고 종료되는지 확인 (assertDoesNotThrow는 생략 가능하지만 명시적으로 표현)
        org.junit.jupiter.api.Assertions.assertDoesNotThrow(() ->
                reviewEventListener.handleImageDeletion(event)
        );

        verify(imageUploadService).deleteReviewImages(anyList());
    }

    @Test
    @DisplayName("리뷰 삭제 처리 중 예외 발생 시 로그 남기고 종료")
    void handleReviewDeleted_Fail() {
        // Given
        Long memberId = 100L;
        Long bookId = 1L;
        ReviewDeletedEvent event = new ReviewDeletedEvent(memberId, bookId);

        // 첫 번째 로직인 통계 업데이트에서 바로 예외 발생
        doThrow(new RuntimeException("DB Connection Error"))
                .when(bookRepository).updateBookReviewStats(bookId);

        // When
        reviewEventListener.handleReviewDeleted(event);

        // Then
        verify(bookRepository).updateBookReviewStats(bookId);

        // 예외가 발생해서 catch로 넘어갔으므로, 그 다음 로직들은 실행되지 않아야 함
        verify(elasticService, never()).decreaseReviewCount(bookId);
        verify(cacheManager, never()).getCache(anyString());
    }
}