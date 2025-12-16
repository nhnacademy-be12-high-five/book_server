package com.nhnacademy.book_server.service.impl;

import com.nhnacademy.book_server.entity.Book;
import com.nhnacademy.book_server.entity.StockHeld;
import com.nhnacademy.book_server.exception.BusinessException;
import com.nhnacademy.book_server.exception.ErrorCode;
import com.nhnacademy.book_server.repository.BookRepository;
import com.nhnacademy.book_server.repository.StockHeldRepository;
import com.nhnacademy.book_server.service.StockService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class StockServiceImpl implements StockService {

    private final BookRepository bookRepository;
    private final StockHeldRepository stockHeldRepository;

    private Book getBookOrThrow(Long bookId) {
        return bookRepository.findById(bookId)
                .orElseThrow(() -> new BusinessException(ErrorCode.BOOK_NOT_FOUND));
    }

    @Override
    @Transactional
    public void holdStock(Long bookId, Integer quantity, String idempotencyKey) {
        Book book = getBookOrThrow(bookId);

        if (stockHeldRepository.findByIdempotencyKey(idempotencyKey).isPresent()) {
            log.warn("Stock hold idempotency: already processed for key={}", idempotencyKey);
            return;
        }

        if (book.getStock() < quantity) {
            log.error("Stock check failed: Book={} has {} stock, but {} requested.", bookId, book.getStock(), quantity);
            throw new BusinessException(ErrorCode.OUT_OF_STOCK);
        }

        String orderKey = idempotencyKey.substring(0, idempotencyKey.indexOf("-"));

        StockHeld held = StockHeld.builder()
                .book(book)
                .quantity(quantity)
                .orderKey(orderKey)
                .idempotencyKey(idempotencyKey)
                .build();
        stockHeldRepository.save(held);

    }

    @Override
    @Transactional
    public void releaseHeldStock(List<Long> bookIds, String orderKey) {
        // TCC Cancel 단계: PENDING 주문 취소 시

        List<StockHeld> heldStocks = stockHeldRepository.findAllByOrderKeyAndBook_IdIn(orderKey, bookIds);

        if (heldStocks.isEmpty()) {
            log.warn("Stock release skipped: No held stock found for orderKey={}", orderKey);
            return;
        }

        // 선점 기록 삭제 (Soft Lock 해제)
        stockHeldRepository.deleteAll(heldStocks);

        // Book.stock은 건드리지 않음
    }

    @Override
    @Transactional
    public void confirmStockDeduction(List<Long> bookIds) {
        // TCC Confirm 단계: 결제 성공 시 확정 차감

        // 해당 bookId들에 대한 모든 선점 기록을 조회합니다.
        List<StockHeld> heldStocks = stockHeldRepository.findAllByBook_IdIn(bookIds);

        for (StockHeld held : heldStocks) {
            Book book = held.getBook();
            Integer quantity = held.getQuantity();

            // 마지막 확인: 선점 상태와 실제 재고 상태의 불일치 여부 확인
            if (book.getStock() < quantity) {
                log.error("CRITICAL ERROR: Stock deduction failed for Book={}, Stock={}, Held={}",
                        book.getId(), book.getStock(), quantity);
                // 🚨 문제점 2 수정: 적절한 BusinessException 사용
                throw new BusinessException(ErrorCode.STOCK_CONFIRMATION_ERROR);
            } // 🚨 문제점 1 수정: if 문의 닫는 중괄호 추가

            // 1. 실제 재고 차감 (Confirm)
            book.setStock(book.getStock() - quantity);

            // 2. 선점 기록 삭제 (Confirm 완료)
            stockHeldRepository.delete(held);
        }

        bookRepository.saveAll(heldStocks.stream().map(StockHeld::getBook).toList());
    }

    @Override
    @Transactional
    public void restoreStock(List<Long> bookIds, String idempotencyKey) {
        // TCC Cancel 단계: WAITING 주문 취소 (환불) 시

        log.error("CRITICAL: Restore stock failed. Item quantities are missing. Order Server MUST provide item quantities for restoration.");
        // 🚨 문제점 3 수정: 재고 복구에 필요한 필수 정보(수량)가 없으므로 실패로 간주하고 예외를 던집니다.
        throw new BusinessException(ErrorCode.EXTERNAL_SERVER_ERROR);

        // 올바른 구현을 위해서는 Order Server가 {BookId, Quantity} DTO 목록을 보내도록 API 명세가 변경되어야 합니다.
    }
}