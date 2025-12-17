package com.nhnacademy.book_server.listener;

import com.nhnacademy.book_server.config.RabbitMqConfig;
import com.nhnacademy.book_server.dto.ReviewCreatedEvent;
import com.nhnacademy.book_server.dto.ReviewImageDeleteEvent;
import com.nhnacademy.book_server.dto.request.PointEarnRequest;
import com.nhnacademy.book_server.entity.Book;
import com.nhnacademy.book_server.entity.BookReviewAi;
import com.nhnacademy.book_server.repository.BookRepository;
import com.nhnacademy.book_server.repository.review.BookReviewAiRepository;
import com.nhnacademy.book_server.repository.review.ReviewRepository;
import com.nhnacademy.book_server.service.MinioImageService;
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
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ReviewEventListenerTest {

    @InjectMocks
    private ReviewEventListener reviewEventListener;

    @Mock private RabbitTemplate rabbitTemplate;
    @Mock private BookRepository bookRepository;
    @Mock private ReviewRepository reviewRepository;
    @Mock private BookReviewAiRepository bookReviewAiRepository;
    @Mock private GeminiTextClientService geminiService;
    @Mock private CacheManager cacheManager;
    @Mock private MinioImageService imageUploadService;
    @Mock private Cache cache;

    // --- handleReviewCreated 테스트 ---

    @Test
    @DisplayName("리뷰 생성 이벤트 처리 - 포인트 적립 메시지 전송 및 통계 업데이트 성공")
    void handleReviewCreated_Success() {
        // given
        Long memberId = 1L;
        Long bookId = 10L;
        String eventType = "EARN_REVIEW";
        ReviewCreatedEvent event = new ReviewCreatedEvent(memberId, bookId, eventType);

        // when
        reviewEventListener.handleReviewCreated(event);

        // then
        // 1. 포인트 메시지 전송 확인
        verify(rabbitTemplate).convertAndSend(
                eq(RabbitMqConfig.POINT_EXCHANGE),
                eq(RabbitMqConfig.ROUTING_KEY),
                any(PointEarnRequest.class)
        );
        // 2. 책 통계 업데이트 확인
        verify(bookRepository).updateBookReviewStats(bookId);
    }

    @Test
    @DisplayName("리뷰 생성 이벤트 - 포인트 전송 실패 시에도 통계 업데이트는 수행되어야 함")
    void handleReviewCreated_PointSendFail() {
        // given
        ReviewCreatedEvent event = new ReviewCreatedEvent(1L, 10L, "EARN_REVIEW");
        willThrow(new RuntimeException("RabbitMQ Error"))
                .given(rabbitTemplate).convertAndSend(anyString(), anyString(), any(Object.class));

        // when
        reviewEventListener.handleReviewCreated(event);

        // then
        verify(rabbitTemplate).convertAndSend(anyString(), anyString(), any(Object.class));
        verify(bookRepository).updateBookReviewStats(10L); // 실패하지 않고 실행됨을 검증
    }

    @Test
    @DisplayName("리뷰 생성 이벤트 - bookId가 null이면 통계 업데이트를 수행하지 않음")
    void handleReviewCreated_NullBookId() {
        // given
        ReviewCreatedEvent event = new ReviewCreatedEvent(1L, null, "EARN_REVIEW");

        // when
        reviewEventListener.handleReviewCreated(event);

        // then
        verify(bookRepository, never()).updateBookReviewStats(any());
        verify(rabbitTemplate).convertAndSend(anyString(), anyString(), any(Object.class)); // 포인트는 실행
    }

    @Test
    @DisplayName("리뷰 생성 이벤트 - 통계 업데이트 실패 시 예외 로그 처리 (메서드는 정상 종료)")
    void handleReviewCreated_StatsUpdateFail() {
        // given
        ReviewCreatedEvent event = new ReviewCreatedEvent(1L, 10L, "EARN_REVIEW");
        willThrow(new RuntimeException("DB Error")).given(bookRepository).updateBookReviewStats(10L);

        // when
        reviewEventListener.handleReviewCreated(event);

        // then
        verify(bookRepository).updateBookReviewStats(10L);
    }


    // --- handleAiSummaryTrigger 테스트 ---

    @Test
    @DisplayName("AI 요약 - 책이 존재하지 않으면 중단")
    void handleAiSummaryTrigger_BookNotFound() {
        // given
        Long bookId = 999L;
        given(bookRepository.findById(bookId)).willReturn(Optional.empty());

        // when
        reviewEventListener.handleAiSummaryTrigger(new ReviewCreatedEvent(1L, bookId, "REVIEW"));

        // then
        verify(reviewRepository, never()).countByBookId(any());
    }

    @Test
    @DisplayName("AI 요약 - 최초 생성 (리뷰 5개 이상) 성공 및 캐시 초기화")
    void handleAiSummaryTrigger_Initial_Success() {
        // given
        Long bookId = 1L;
        Book book = new Book();
        ReflectionTestUtils.setField(book, "title", "Test Book");

        given(bookRepository.findById(bookId)).willReturn(Optional.of(book));
        given(reviewRepository.countByBookId(bookId)).willReturn(5L); // Threshold: 5
        given(reviewRepository.getAverageRating(bookId)).willReturn(4.5);
        given(bookReviewAiRepository.findByBook_Id(bookId)).willReturn(Optional.empty()); // 기존 요약 없음

        given(reviewRepository.findReviewContentsByBookId(eq(bookId), any(Pageable.class)))
                .willReturn(List.of("Content1", "Content2"));
        given(geminiService.getReviewSummary(anyString(), anyList())).willReturn("Summary");
        given(cacheManager.getCache("bookDetail")).willReturn(cache);

        // when
        reviewEventListener.handleAiSummaryTrigger(new ReviewCreatedEvent(1L, bookId, "REVIEW"));

        // then
        verify(bookReviewAiRepository).save(any(BookReviewAi.class));
        verify(cache).evict(bookId);
    }

    @Test
    @DisplayName("AI 요약 - 업데이트 (리뷰 수 차이 10개 이상)")
    void handleAiSummaryTrigger_Update_ByCount() {
        // given
        Long bookId = 1L;
        Book book = new Book();
        ReflectionTestUtils.setField(book, "title", "Test Book");

        // 기존: 리뷰 10개, 평점 4.5
        BookReviewAi lastSummary = new BookReviewAi(book, "Old Summary", 10L, 4.5);

        given(bookRepository.findById(bookId)).willReturn(Optional.of(book));
        given(reviewRepository.countByBookId(bookId)).willReturn(20L); // 현재: 20개 (차이 10)
        given(reviewRepository.getAverageRating(bookId)).willReturn(4.5); // 평점 변화 없음
        given(bookReviewAiRepository.findByBook_Id(bookId)).willReturn(Optional.of(lastSummary));

        given(reviewRepository.findReviewContentsByBookId(eq(bookId), any())).willReturn(List.of("c"));
        given(geminiService.getReviewSummary(any(), any())).willReturn("New Summary");

        // when
        reviewEventListener.handleAiSummaryTrigger(new ReviewCreatedEvent(1L, bookId, "REVIEW"));

        // then
        verify(geminiService).getReviewSummary(any(), any());
        verify(bookReviewAiRepository).save(lastSummary); // Update check
    }

    @Test
    @DisplayName("AI 요약 - 업데이트 (평점 차이 0.5 이상)")
    void handleAiSummaryTrigger_Update_ByRating() {
        // given
        Long bookId = 1L;
        Book book = new Book();
        ReflectionTestUtils.setField(book, "title", "Test Book");

        // 기존: 리뷰 10개, 평점 4.0
        BookReviewAi lastSummary = new BookReviewAi(book, "Old Summary", 10L, 4.0);

        given(bookRepository.findById(bookId)).willReturn(Optional.of(book));
        given(reviewRepository.countByBookId(bookId)).willReturn(15L); // 리뷰 수 변화 5 (Threshold 10 미만)
        given(reviewRepository.getAverageRating(bookId)).willReturn(4.6); // 평점 4.6 (차이 0.6 >= 0.5)
        given(bookReviewAiRepository.findByBook_Id(bookId)).willReturn(Optional.of(lastSummary));

        given(reviewRepository.findReviewContentsByBookId(eq(bookId), any())).willReturn(List.of("c"));
        given(geminiService.getReviewSummary(any(), any())).willReturn("New Summary");

        // when
        reviewEventListener.handleAiSummaryTrigger(new ReviewCreatedEvent(1L, bookId, "REVIEW"));

        // then
        verify(geminiService).getReviewSummary(any(), any());
        verify(bookReviewAiRepository).save(lastSummary);
    }

    @Test
    @DisplayName("AI 요약 - 업데이트 조건 미달 (변경 없음)")
    void handleAiSummaryTrigger_NoUpdate() {
        // given
        Long bookId = 1L;
        Book book = new Book();
        BookReviewAi lastSummary = new BookReviewAi(book, "Old", 10L, 4.0);

        given(bookRepository.findById(bookId)).willReturn(Optional.of(book));
        given(bookReviewAiRepository.findByBook_Id(bookId)).willReturn(Optional.of(lastSummary));
        given(reviewRepository.countByBookId(bookId)).willReturn(15L); // 차이 5
        given(reviewRepository.getAverageRating(bookId)).willReturn(4.2); // 차이 0.2

        // when
        reviewEventListener.handleAiSummaryTrigger(new ReviewCreatedEvent(1L, bookId, "REVIEW"));

        // then
        verify(geminiService, never()).getReviewSummary(any(), any());
    }

    @Test
    @DisplayName("AI 요약 - Gemini 서비스 호출 중 예외 발생 시 안전하게 처리")
    void handleAiSummaryTrigger_Exception() {
        // given
        Long bookId = 1L;
        Book book = new Book();
        ReflectionTestUtils.setField(book, "title", "Test");

        given(bookRepository.findById(bookId)).willReturn(Optional.of(book));
        given(reviewRepository.countByBookId(bookId)).willReturn(5L);
        given(bookReviewAiRepository.findByBook_Id(bookId)).willReturn(Optional.empty());
        given(reviewRepository.findReviewContentsByBookId(eq(bookId), any())).willReturn(List.of("review"));

        // 예외 발생 설정
        willThrow(new RuntimeException("Gemini API Error")).given(geminiService).getReviewSummary(any(), any());

        // when
        reviewEventListener.handleAiSummaryTrigger(new ReviewCreatedEvent(1L, bookId, "REVIEW"));

        // then
        verify(bookReviewAiRepository, never()).save(any()); // 저장되지 않아야 함
    }


    // --- handleImageDeletion 테스트 ---

    @Test
    @DisplayName("이미지 삭제 이벤트 - 정상 삭제")
    void handleImageDeletion_Success() {
        // given
        List<String> urls = List.of("url1", "url2");
        ReviewImageDeleteEvent event = new ReviewImageDeleteEvent(urls);

        // when
        reviewEventListener.handleImageDeletion(event);

        // then
        verify(imageUploadService).deleteImages(urls);
    }

    @Test
    @DisplayName("이미지 삭제 이벤트 - URL 리스트가 null이거나 비어있으면 무시")
    void handleImageDeletion_Empty() {
        // when
        reviewEventListener.handleImageDeletion(new ReviewImageDeleteEvent(null));
        reviewEventListener.handleImageDeletion(new ReviewImageDeleteEvent(Collections.emptyList()));

        // then
        verify(imageUploadService, never()).deleteImages(any());
    }

    @Test
    @DisplayName("이미지 삭제 이벤트 - 서비스 예외 발생 시 로그 처리 (중단되지 않음)")
    void handleImageDeletion_Exception() {
        // given
        List<String> urls = List.of("url1");
        ReviewImageDeleteEvent event = new ReviewImageDeleteEvent(urls);
        willThrow(new RuntimeException("S3 Error")).given(imageUploadService).deleteImages(urls);

        // when
        reviewEventListener.handleImageDeletion(event);

        // then
        verify(imageUploadService).deleteImages(urls);
    }
}