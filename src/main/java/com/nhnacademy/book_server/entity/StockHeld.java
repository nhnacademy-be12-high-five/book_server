package com.nhnacademy.book_server.entity;

import jakarta.persistence.*;
import lombok.*;

// 주문 생성 시 임시로 선점한 재고를 기록하는 엔티티 (TCC Try/Cancel/Confirm의 핵심)
@Entity
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Table(name = "stock_held") // 한글 테이블명 적용
public class StockHeld {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "book_id", nullable = false)
    private Book book;

    @Column(name = "quantity", nullable = false)
    private Integer quantity;

    // 주문 고유 키 (OrderKey)를 사용하여 해당 선점 기록을 추적
    @Column(name = "order_key", nullable = false, length = 36)
    private String orderKey;

    // 해당 book_id와 order_key 조합은 유일해야 함 (중복 선점 방지용 멱등성 키)
    @Column(name = "idempotency_key", nullable = false, unique = true, length = 60)
    private String idempotencyKey;
}