package com.nhnacademy.book_server.listener;

import com.nhnacademy.book_server.config.RabbitMqConfig;
import com.nhnacademy.book_server.dto.event.ReviewCreatedEvent;
import com.nhnacademy.book_server.dto.event.ReviewImageDeleteEvent;
import com.nhnacademy.book_server.dto.request.PointEarnRequest;
import com.nhnacademy.book_server.entity.Book;
import com.nhnacademy.book_server.entity.BookReviewAi;
import com.nhnacademy.book_server.repository.BookRepository;
import com.nhnacademy.book_server.repository.BookReviewAiRepository;
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
    @Mock private Cache cache;

    @Test
    @DisplayName("기본 리뷰 이벤트 처리 (포인트, 통계)")
    void handleReviewCreated_Success() {
        ReviewCreatedEvent event = new ReviewCreatedEvent(100L, 1L, "EARN_REVIEW");

        reviewEventListener.handleReviewCreated(event);

        verify(rabbitTemplate).convertAndSend(eq(RabbitMqConfig.POINT_EXCHANGE), eq(RabbitMqConfig.ROUTING_KEY), any(PointEarnRequest.class));
        verify(bookRepository).updateBookReviewStats(1L);
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
}