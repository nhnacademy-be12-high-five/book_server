//package com.nhnacademy.book_server.service.review;
//
//import com.nhnacademy.book_server.dto.ReviewCreatedEvent;
//import com.nhnacademy.book_server.entity.Book;
//import com.nhnacademy.book_server.entity.BookReviewAi;
//import com.nhnacademy.book_server.listener.ReviewEventListener;
//import com.nhnacademy.book_server.repository.BookRepository;
//import com.nhnacademy.book_server.repository.BookReviewAiRepository;
//import com.nhnacademy.book_server.repository.ReviewRepository;
//import com.nhnacademy.book_server.service.search.GeminiTextClientService;
//
//
//import org.junit.jupiter.api.DisplayName;
//import org.junit.jupiter.api.Test;
//import org.junit.jupiter.api.extension.ExtendWith;
//import org.mockito.InjectMocks;
//import org.mockito.Mock;
//import org.mockito.junit.jupiter.MockitoExtension;
//import org.springframework.data.domain.Pageable;
//
//import java.util.List;
//import java.util.Optional;
//
//import static org.mockito.ArgumentMatchers.any;
//import static org.mockito.ArgumentMatchers.eq;
//import static org.mockito.Mockito.*;
//
//@ExtendWith(MockitoExtension.class)
//class ReviewEventListenerTest {
//
//    @InjectMocks
//    private ReviewEventListener reviewEventListener;
//
//    @Mock private BookRepository bookRepository;
//    @Mock private ReviewRepository reviewRepository;
//    @Mock private BookReviewAiRepository bookReviewAiRepository;
//    @Mock
//    private GeminiTextClientService geminiService;
//
//    @Test
//    @DisplayName("최초 생성: 리뷰가 5개 미만이면 AI 요약을 실행하지 않는다")
//    void testInitialTrigger_Fail() {
//        // Given
//        Long bookId = 1L;
//        when(bookRepository.findById(bookId)).thenReturn(Optional.of(new Book())); // Book 객체 모킹
//        when(reviewRepository.countByBookId(bookId)).thenReturn(3L); // 리뷰 3개 (기준 미달)
//        when(bookReviewAiRepository.findByBook_Id(bookId)).thenReturn(Optional.empty()); // 기존 요약 없음
//
//        // When
//        reviewEventListener.handleAiSummaryTrigger(new ReviewCreatedEvent(1L, bookId, "REVIEW"));
//
//        // Then
//        verify(geminiService, never()).getReviewSummary(any(), any()); // AI 호출 안 함 확인
//        verify(reviewRepository, never()).save(any()); // 저장 안 함 확인
//    }
//
//    @Test
//    @DisplayName("최초 생성: 리뷰가 5개 이상이면 AI 요약을 실행하고 저장한다")
//    void testInitialTrigger_Success() {
//        // Given
//        Long bookId = 1L;
//        Book book = new Book(); // 필드 세팅 필요시 추가
//        when(bookRepository.findById(bookId)).thenReturn(Optional.of(book));
//        when(reviewRepository.countByBookId(bookId)).thenReturn(10L); // 리뷰 10개 (기준 충족)
//        when(bookReviewAiRepository.findByBook_Id(bookId)).thenReturn(Optional.empty());
//
//        // AI 응답 모킹
//        when(reviewRepository.findReviewContentsByBookId(eq(bookId), any(Pageable.class)))
//                .thenReturn(List.of("좋아요", "별로예요"));
//        when(geminiService.getReviewSummary(any(), any())).thenReturn("AI 요약 결과입니다.");
//
//        // When
//        reviewEventListener.handleAiSummaryTrigger(new ReviewCreatedEvent(1L, bookId, "REVIEW"));
//
//        // Then
//        verify(geminiService, times(1)).getReviewSummary(any(), any()); // AI 호출 확인
//        verify(bookReviewAiRepository, times(1)).save(any(BookReviewAi.class)); // 저장 확인
//    }
//
//    @Test
//    @DisplayName("업데이트: 신규 리뷰가 10개 미만이면 갱신하지 않는다")
//    void testUpdateTrigger_Fail() {
//        // Given
//        Long bookId = 1L;
//        Book book = new Book();
//        book.setAverageRating(4.5);
//        BookReviewAi lastSummary = new BookReviewAi(book, "Old Summary", 10L, 4.5);
//
//        when(bookRepository.findById(bookId)).thenReturn(Optional.of(book));
//        when(bookReviewAiRepository.findByBook_Id(bookId)).thenReturn(Optional.of(lastSummary));
//        when(reviewRepository.countByBookId(bookId)).thenReturn(15L); // 현재 15개 (차이 5개 < 10개)
//
//        // When
//        reviewEventListener.handleAiSummaryTrigger(new ReviewCreatedEvent(1L, bookId, "REVIEW"));
//
//        // Then
//        verify(geminiService, never()).getReviewSummary(any(), any());
//    }
//
//    @Test
//    @DisplayName("업데이트: 신규 리뷰가 10개 이상이면 갱신한다")
//    void testUpdateTrigger_Success() {
//        // Given
//        Long bookId = 1L;
//        Book book = new Book();
//        // 기존 요약 정보 (리뷰 10개일 때 생성됨)
//        BookReviewAi lastSummary = new BookReviewAi(book, "Old Summary", 10L, 4.5);
//
//        when(bookRepository.findById(bookId)).thenReturn(Optional.of(book));
//        when(bookReviewAiRepository.findByBook_Id(bookId)).thenReturn(Optional.of(lastSummary));
//        when(reviewRepository.countByBookId(bookId)).thenReturn(25L); // 현재 25개 (차이 15개 >= 10개)
//
//        // AI 응답 모킹
//        when(reviewRepository.findReviewContentsByBookId(eq(bookId), any(Pageable.class)))
//                .thenReturn(List.of("새로운 리뷰들..."));
//        when(geminiService.getReviewSummary(any(), any())).thenReturn("새로운 요약");
//
//        // When
//        reviewEventListener.handleAiSummaryTrigger(new ReviewCreatedEvent(1L, bookId, "REVIEW"));
//
//        // Then
//        verify(geminiService, times(1)).getReviewSummary(any(), any());
//        verify(bookReviewAiRepository, times(1)).save(any(BookReviewAi.class));
//    }
//}