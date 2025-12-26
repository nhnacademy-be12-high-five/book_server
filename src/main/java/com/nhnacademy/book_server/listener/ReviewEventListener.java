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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.cache.Cache;
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
    private final MinioImageService imageUploadService;
    private final ElasticService elasticService;


    private static final int FIRST_TRIGGER_THRESHOLD = 5;
    private static final int REVIEW_COUNT_DELTA_THRESHOLD = 10;
    private static final double RATING_DELTA_THRESHOLD = 0.5;
    private static final int RECENT_REVIEWS_LIMIT = 30;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void handleReviewCreated(ReviewCreatedEvent event) {
        sendPointMessage(event);
        updateBookStats(event);
        evictBookDetailCache(event.bookId());
        elasticService.increaseReviewCount(event.bookId());
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void handleAiSummaryTrigger(ReviewCreatedEvent event) {
        Long bookId = event.bookId();
        log.info("AI 요약 트리거 확인 시작: bookId={}", bookId);

        Book book = bookRepository.findById(bookId).orElse(null);
        if (book == null) return;

        long currentReviewCount = reviewRepository.countByBookId(bookId);
        Double currentRating = reviewRepository.getAverageRating(bookId);
        BookReviewAi lastSummary = bookAiSummaryRepository.findByBook_Id(bookId).orElse(null);

        boolean shouldTrigger = false;

        if (lastSummary == null) {
            if (currentReviewCount >= FIRST_TRIGGER_THRESHOLD) shouldTrigger = true;
        } else {
            long diffCount = currentReviewCount - lastSummary.getLastReviewCount();
            double diffRating = Math.abs(currentRating - lastSummary.getLastAvgRating());
            if (diffCount >= REVIEW_COUNT_DELTA_THRESHOLD || diffRating >= RATING_DELTA_THRESHOLD) {
                shouldTrigger = true;
            }
        }

        if (shouldTrigger) {
            try {
                List<String> recentReviews = reviewRepository.findReviewContentsByBookId(bookId, PageRequest.of(0, RECENT_REVIEWS_LIMIT));
                String summaryText = geminiService.getReviewSummary(book.getTitle(), recentReviews);

                if (lastSummary == null) {
                    bookAiSummaryRepository.save(new BookReviewAi(book, summaryText, currentReviewCount, currentRating));
                } else {
                    lastSummary.updateSummary(summaryText, currentReviewCount, currentRating);
                    bookAiSummaryRepository.save(lastSummary);
                }

                // 캐시 초기화
                evictBookDetailCache(bookId);

                log.info("AI 요약 업데이트 완료: bookId={}", bookId);
            } catch (Exception e) {
                log.error("AI 요약 생성 중 실패: bookId={}", bookId, e);
            }
        }
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleImageDeletion(ReviewImageDeleteEvent event){
        if (event.imageUrls() == null || event.imageUrls().isEmpty()) {
            return;
        }
        try {
            imageUploadService.deleteReviewImages(event.imageUrls());
            log.info("S3 이미지 삭제 완료: {}장", event.imageUrls().size());
        } catch (Exception e) {
            log.error("S3 이미지 삭제 실패: {}", event.imageUrls(), e);
        }
    }

    private void sendPointMessage(ReviewCreatedEvent event) {
        try {
            PointEarnRequest message = new PointEarnRequest(
                    event.memberId(),
                    event.eventType(),
                    null, null
            );
            rabbitTemplate.convertAndSend(
                    RabbitMqConfig.POINT_EXCHANGE,
                    RabbitMqConfig.ROUTING_KEY,
                    message
            );
            log.info("포인트 메시지 전송: memberId={}", event.memberId());
        } catch (Exception e) {
            log.error("포인트 메시지 전송 실패: memberId={}", event.memberId(), e);
        }
    }

    private void updateBookStats(ReviewCreatedEvent event) {
        try {
            bookRepository.updateBookReviewStats(event.bookId());
            log.info("책 통계 업데이트: bookId={}", event.bookId());
        } catch (Exception e) {
            log.error("책 통계 업데이트 실패: bookId={}", event.bookId(), e);
        }
    }

    // 안전한 캐시 삭제 헬퍼 메서드
    private void evictBookDetailCache(Long bookId) {
        if (bookId == null) return;
        try {
            Cache cache = cacheManager.getCache("bookDetail");
            if (cache != null) {
                cache.evict(bookId);
                log.info("Cache 'bookDetail' evicted for bookId={}", bookId);
            }
        } catch (Exception e) {
            log.warn("Cache eviction failed for bookId={}", bookId, e);
        }
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void handleReviewDeleted(ReviewDeletedEvent event) {
        try {
            // RDB 통계 재계산(원래 updateBookStats는 생성 때만 호출 중이므로 삭제 때도 호출)
            bookRepository.updateBookReviewStats(event.bookId());

            // ES reviewCount -1
            elasticService.decreaseReviewCount(event.bookId());

            // 상세 캐시 삭제(있다면)
            evictBookDetailCache(event.bookId());

            log.info("리뷰 삭제 반영 완료: bookId={}", event.bookId());
        } catch (Exception e) {
            log.error("리뷰 삭제 반영 실패: bookId={}", event.bookId(), e);
        }
    }

}