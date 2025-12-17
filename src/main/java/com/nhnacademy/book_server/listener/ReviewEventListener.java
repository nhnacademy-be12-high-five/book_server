package com.nhnacademy.book_server.listener;

import com.nhnacademy.book_server.config.RabbitMqConfig;
import com.nhnacademy.book_server.dto.ReviewCreatedEvent;
import com.nhnacademy.book_server.dto.request.PointEarnRequest;
import com.nhnacademy.book_server.entity.Book;
import com.nhnacademy.book_server.entity.BookReviewAi;
import com.nhnacademy.book_server.repository.BookRepository;
import com.nhnacademy.book_server.repository.BookReviewAiRepository;
import com.nhnacademy.book_server.repository.ReviewRepository;
import com.nhnacademy.book_server.service.search.GeminiTextClientService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.cache.CacheManager;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class ReviewEventListener {

    private final RabbitTemplate rabbitTemplate;
    private final BookRepository bookRepository;
    private final ReviewRepository reviewRepository;
    private final BookReviewAiRepository bookAiSummaryRepository;
    private final GeminiTextClientService geminiService;
    private final CacheManager cacheManager;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void handleReviewCreated(ReviewCreatedEvent event) {
        sendPointMessage(event);
        updateBookStats(event);
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void handleAiSummaryTrigger(ReviewCreatedEvent event) {
        log.info("📣 리뷰 이벤트 수신 확인! bookId={}", event.bookId());
        Long bookId = event.bookId();

        Book book = bookRepository.findById(bookId).orElse(null);
        if (book == null) return;

        long currentReviewCount = reviewRepository.countByBookId(bookId);
        Double currentRating = book.getAverageRating();
        if (currentRating == null) currentRating = 0.0;

        BookReviewAi lastSummary = bookAiSummaryRepository.findByBook_Id(bookId).orElse(null);

        boolean shouldTrigger = false;

        if (lastSummary == null) {
            if (currentReviewCount >= 5) shouldTrigger = true;
        } else {
            long diffCount = currentReviewCount - lastSummary.getLastReviewCount();

            double diffRating = Math.abs(currentRating - lastSummary.getLastAvgRating());

            if (diffCount >= 10 || diffRating >= 0.5) {
                shouldTrigger = true;
                log.info("AI 요약 트리거 발동 - 책: {}, 리뷰증가: {}, 평점변화: {}", bookId, diffCount, diffRating);
            }
        }

        if (shouldTrigger) {
            try {
                List<String> recentReviews = reviewRepository.findReviewContentsByBookId(bookId, PageRequest.of(0, 30));

                String summaryText = geminiService.getReviewSummary(book.getTitle(), recentReviews);

                if (lastSummary == null) {
                    bookAiSummaryRepository.save(new BookReviewAi(book, summaryText, currentReviewCount, currentRating));
                } else {
                    lastSummary.updateSummary(summaryText, currentReviewCount, currentRating);
                    bookAiSummaryRepository.save(lastSummary);
                }
                log.info("AI 요약 업데이트 완료: bookId={}", bookId);

                if (cacheManager.getCache("bookDetail") != null) {
                    cacheManager.getCache("bookDetail").evict(bookId);
                    log.info("♻️ Spring Cache 초기화 완료: bookId={}", bookId);
                }

            } catch (Exception e) {
                log.error("AI 요약 생성 중 실패", e);
            }
        }
    }

    private void sendPointMessage(ReviewCreatedEvent event) {
        PointEarnRequest message = new PointEarnRequest(
                event.memberId(),
                event.eventType(),
                null, null
        );
        try {
            rabbitTemplate.convertAndSend(
                    RabbitMqConfig.POINT_EXCHANGE,
                    RabbitMqConfig.ROUTING_KEY,
                    message
            );
            log.info("포인트 적립 메시지 전송 완료: memberId={}", event.memberId());
        } catch (Exception e) {
            log.error("메시지 전송 실패 (포인트 누락 가능성 있음)", e);
        }
    }

    private void updateBookStats(ReviewCreatedEvent event) {
        if (event.bookId() == null) {
            log.warn("BookId가 없어 평점 업데이트 실패");
            return;
        }
        try {
            bookRepository.updateBookReviewStats(event.bookId());
            log.info("책 평점/리뷰수 업데이트 완료: bookId={}", event.bookId());
        } catch (Exception e) {
            log.error("책 평점 업데이트 실패: bookId={}", event.bookId(), e);
        }
    }
}