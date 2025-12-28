package com.nhnacademy.book_server.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StockIdempotencyRecord {

    @Id
    private String idempotencyKey; // 요청 고유 키 (UUID 등)

    private String status;         // 처리 상태 (SUCCESS, FAILED)
    private String type;           // 작업 유형 (RESTORE, CONFIRM 등)
    private LocalDateTime createdAt;
}