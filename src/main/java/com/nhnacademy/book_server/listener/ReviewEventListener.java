package com.nhnacademy.book_server.listener;

import com.nhnacademy.book_server.config.RabbitMqConfig;
import com.nhnacademy.book_server.dto.ReviewCreatedEvent;
import com.nhnacademy.book_server.dto.request.PointEarnRequest;
import com.nhnacademy.book_server.repository.BookRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
@Slf4j
public class ReviewEventListener {

    private final RabbitTemplate rabbitTemplate;
    private final BookRepository bookRepository;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void handleReviewCreated(ReviewCreatedEvent event) {

        sendPointMessage(event);

        updateBookStats(event);
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