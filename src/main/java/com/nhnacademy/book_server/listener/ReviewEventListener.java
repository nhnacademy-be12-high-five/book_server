package com.nhnacademy.book_server.listener;

import com.nhnacademy.book_server.config.RabbitMqConfig;
import com.nhnacademy.book_server.dto.ReviewCreatedEvent;
import com.nhnacademy.book_server.dto.request.PointEarnRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
@Slf4j
public class ReviewEventListener {

    private final RabbitTemplate rabbitTemplate;

    // DB 저장이 확실하게 된 후 실행되도록 설정
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleReviewCreated(ReviewCreatedEvent event) {

        PointEarnRequest message = new PointEarnRequest(
                event.getMemberId(),
                event.getEventType(),
                null, null
        );

        try {
            rabbitTemplate.convertAndSend(
                    RabbitMqConfig.POINT_EXCHANGE,
                    RabbitMqConfig.ROUTING_KEY,
                    message
            );
            log.info("포인트 적립 메시지 전송 완료: memberId={}", event.getMemberId());

        } catch (Exception e) {
            log.error("메시지 전송 실패 (리뷰는 저장됨, 포인트 누락 가능성 있음)", e);
        }
    }
}
