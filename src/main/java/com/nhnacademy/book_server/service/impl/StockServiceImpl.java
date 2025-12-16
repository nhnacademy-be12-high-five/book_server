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

        // 1. 멱등성 검사
        if (stockHeldRepository.findByIdempotencyKey(idempotencyKey).isPresent()) {
            log.warn("Stock hold idempotency: already processed for key={}", idempotencyKey);
            return;
        }

        // 2. 재고 확인 (TCC Try)
        if (book.getStock() < quantity) {
            log.error("Stock check failed: Book={} has {} stock, but {} requested.", bookId, book.getStock(), quantity);
            throw new BusinessException(ErrorCode.OUT_OF_STOCK);
        }

        // OrderKey 추출 (예: "UUID-BOOKID"에서 "UUID" 부분)
        String orderKey = idempotencyKey.substring(0, idempotencyKey.indexOf("-"));

        // 3. 재고 선점 기록 (Soft Lock 저장)
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
        // TCC Cancel 단계: PENDING 주문 취소 시 (Try 단계 보상)

        List<StockHeld> heldStocks = stockHeldRepository.findAllByOrderKeyAndBook_IdIn(orderKey, bookIds);

        if (heldStocks.isEmpty()) {
            log.warn("Stock release skipped: No held stock found for orderKey={}", orderKey);
            return;
        }

        // 선점 기록 삭제 (Soft Lock 해제)
        stockHeldRepository.deleteAll(heldStocks);
    }

    @Override
    @Transactional
    public void confirmStockDeduction(List<Long> bookIds) {
        // TCC Confirm 단계: 결제 성공 시 확정 차감

        List<StockHeld> heldStocks = stockHeldRepository.findAllByBook_IdIn(bookIds);

        for (StockHeld held : heldStocks) {
            Book book = held.getBook();
            Integer quantity = held.getQuantity();

            // 마지막 확인: 선점 상태와 실제 재고 상태의 불일치 여부 확인
            if (book.getStock() < quantity) {
                log.error("CRITICAL ERROR: Stock deduction failed for Book={}, Stock={}, Held={}",
                        book.getId(), book.getStock(), quantity);
                // 치명적인 오류 발생 시 적절한 에러 코드를 던집니다.
                throw new BusinessException(ErrorCode.STOCK_CONFIRMATION_ERROR);
            }

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
        // TCC Cancel 단계: WAITING 주문 취소 (환불) 시 재고 복구

        // WAITING 취소(환불)는 실제 재고를 복구해야 하므로, 수량 정보가 필수입니다.
        // 현재 API는 bookIds만 받으므로, Order Server가 수량 정보를 보내도록 API를 변경해야 합니다.

        log.error("CRITICAL: Restore stock failed. Item quantities are missing. Order Server MUST provide item quantities for restoration.");
        throw new BusinessException(ErrorCode.EXTERNAL_SERVER_ERROR);
    }
}