package com.nhnacademy.book_server.service.review;

import com.nhnacademy.book_server.dto.ReviewCreatedEvent;
import com.nhnacademy.book_server.entity.Book;
import com.nhnacademy.book_server.entity.BookReviewAi;
import com.nhnacademy.book_server.listener.ReviewEventListener;
import com.nhnacademy.book_server.repository.BookRepository;
import com.nhnacademy.book_server.repository.review.BookReviewAiRepository;
import com.nhnacademy.book_server.repository.review.ReviewRepository;
import com.nhnacademy.book_server.service.search.GeminiTextClientService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ReviewEventListenerTest {

    @InjectMocks
    private ReviewEventListener reviewEventListener;

    @Mock private BookRepository bookRepository;
    @Mock private ReviewRepository reviewRepository;
    @Mock private BookReviewAiRepository bookReviewAiRepository;
    @Mock private GeminiTextClientService geminiService;

    // [필수] CacheManager NPE 방지를 위한 Mock 추가
    @Mock private CacheManager cacheManager;
    @Mock private Cache cache;

    @Test
    @DisplayName("최초 생성: 리뷰가 5개 미만이면 AI 요약을 실행하지 않는다")
    void testInitialTrigger_Fail() {
        // Given
        Long bookId = 1L;
        when(bookRepository.findById(bookId)).thenReturn(Optional.of(new Book()));
        when(reviewRepository.countByBookId(bookId)).thenReturn(3L);
        when(bookReviewAiRepository.findByBook_Id(bookId)).thenReturn(Optional.empty());

        // When
        reviewEventListener.handleAiSummaryTrigger(new ReviewCreatedEvent(1L, bookId, "REVIEW"));

        // Then
        verify(geminiService, never()).getReviewSummary(any(), any());
        verify(reviewRepository, never()).save(any());
    }

    @Test
    @DisplayName("최초 생성: 리뷰가 5개 이상이면 AI 요약을 실행하고 저장한다")
    void testInitialTrigger_Success() {
        // Given
        Long bookId = 1L;
        Book book = new Book();
        book.setTitle("Test Book"); // Title null 방지

        when(bookRepository.findById(bookId)).thenReturn(Optional.of(book));
        when(reviewRepository.countByBookId(bookId)).thenReturn(10L);
        when(bookReviewAiRepository.findByBook_Id(bookId)).thenReturn(Optional.empty());

        // AI 요약 생성에 필요한 리뷰 리스트 반환 설정
        when(reviewRepository.findReviewContentsByBookId(eq(bookId), any(Pageable.class)))
                .thenReturn(List.of("좋아요", "별로예요"));
        when(geminiService.getReviewSummary(any(), any())).thenReturn("AI 요약 결과입니다.");

        // 캐시 동작 Mocking
        when(cacheManager.getCache("bookDetail")).thenReturn(cache);

        // When
        reviewEventListener.handleAiSummaryTrigger(new ReviewCreatedEvent(1L, bookId, "REVIEW"));

        // Then
        verify(geminiService, times(1)).getReviewSummary(any(), any());
        verify(bookReviewAiRepository, times(1)).save(any(BookReviewAi.class));
        verify(cache, times(1)).evict(bookId);
    }

    @Test
    @DisplayName("업데이트: 신규 리뷰가 10개 미만이고 평점 변화가 적으면 갱신하지 않는다")
    void testUpdateTrigger_Fail() {
        Long bookId = 1L;

        Book book = new Book();
        book.setTitle("Test Book");

        BookReviewAi lastSummary = new BookReviewAi(book, "Old Summary", 10L, 0.0);

        when(bookRepository.findById(bookId)).thenReturn(Optional.of(book));
        when(bookReviewAiRepository.findByBook_Id(bookId)).thenReturn(Optional.of(lastSummary));

        when(reviewRepository.countByBookId(bookId)).thenReturn(15L);

        reviewEventListener.handleAiSummaryTrigger(new ReviewCreatedEvent(1L, bookId, "REVIEW"));

        verify(geminiService, never()).getReviewSummary(any(), any());
    }

    @Test
    @DisplayName("업데이트: 신규 리뷰가 10개 이상이면 갱신한다")
    void testUpdateTrigger_Success() {
        // Given
        Long bookId = 1L;
        Book book = new Book();
        book.setTitle("Test Book");
        book.setAverageRating(4.5);

        // 실제 객체 사용
        BookReviewAi lastSummary = new BookReviewAi(book, "Old Summary", 10L, 4.5);

        when(bookRepository.findById(bookId)).thenReturn(Optional.of(book));
        when(bookReviewAiRepository.findByBook_Id(bookId)).thenReturn(Optional.of(lastSummary));

        // 현재 리뷰 25개 (25 - 10 = 15개 차이 -> 10개 이상이므로 트리거 발동!)
        when(reviewRepository.countByBookId(bookId)).thenReturn(25L);

        when(reviewRepository.findReviewContentsByBookId(eq(bookId), any(Pageable.class)))
                .thenReturn(List.of("새로운 리뷰들..."));
        when(geminiService.getReviewSummary(any(), any())).thenReturn("새로운 요약");

        when(cacheManager.getCache("bookDetail")).thenReturn(cache);

        // When
        reviewEventListener.handleAiSummaryTrigger(new ReviewCreatedEvent(1L, bookId, "REVIEW"));

        // Then
        verify(geminiService, times(1)).getReviewSummary(any(), any());
        verify(bookReviewAiRepository, times(1)).save(lastSummary);
    }
}