package com.nhnacademy.book_server.service.impl;

import com.nhnacademy.book_server.dto.request.StockRequest;
import com.nhnacademy.book_server.entity.Book;
import com.nhnacademy.book_server.entity.StockHeld;
import com.nhnacademy.book_server.entity.StockIdempotencyRecord;
import com.nhnacademy.book_server.exception.BusinessException;
import com.nhnacademy.book_server.exception.ErrorCode;
import com.nhnacademy.book_server.repository.BookRepository;
import com.nhnacademy.book_server.repository.StockHeldRepository;
import com.nhnacademy.book_server.repository.StockIdempotencyRepository;
import com.nhnacademy.book_server.service.StockService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class StockServiceImpl implements StockService {

    private final BookRepository bookRepository;
    private final StockHeldRepository stockHeldRepository;
    private final StockIdempotencyRepository idempotencyRepository;

    private Book getBookOrThrow(Long bookId) {
        return bookRepository.findById(bookId)
                .orElseThrow(() -> new BusinessException(ErrorCode.BOOK_NOT_FOUND));
    }

    @Override
    @Transactional
    public void holdStock(Long bookId, Integer quantity, String idempotencyKey) {
        // 1. 멱등성 검사
        if (stockHeldRepository.findByIdempotencyKey(idempotencyKey).isPresent()) {
            log.warn("[멱등 처리] 이미 처리된 재고 선점 요청 Key={}", idempotencyKey);
            return;
        }

        Book book = getBookOrThrow(bookId);

        // 2. 가용 재고 확인 (전체 재고 - 이미 선점된 수량)
        Integer currentHeldQuantity = stockHeldRepository.sumHeldQuantityByBookId(bookId);
        int availableStock = book.getStock() - currentHeldQuantity;

        if (availableStock < quantity) {
            log.warn("[재고 선점 실패: 재고 부족] BookId={}, Stock={}, Held={}, Requested={}",
                    bookId, book.getStock(), currentHeldQuantity, quantity);
            throw new BusinessException(ErrorCode.OUT_OF_STOCK);
        }

        // OrderKey 추출 (예: "UUID-BOOKID"에서 "UUID" 부분)
        String orderKey = idempotencyKey.substring(0, idempotencyKey.indexOf("-"));

        // 3. 재고 선점 (Soft Lock)
        StockHeld held = StockHeld.builder()
                .book(book)
                .quantity(quantity)
                .orderKey(orderKey)
                .idempotencyKey(idempotencyKey)
                .build();

        stockHeldRepository.save(held);
        log.info("[재고 선점 완료] BookId={}, Quantity={}, OrderKey={}", bookId, quantity, orderKey);
    }

    @Override
    @Transactional
    public void holdStockBatch(List<StockRequest> requests, String orderKey) {
        if (requests == null || requests.isEmpty()) return;

        List<Long> bookIds = requests.stream()
                .map(StockRequest::getBookId)
                .toList();

        // 1. 책 정보 및 현재 선점 수량 조회
        Map<Long, Book> bookMap = bookRepository.findAllById(bookIds).stream()
                .collect(Collectors.toMap(Book::getId, Function.identity()));

        if (bookMap.size() != bookIds.size()) {
            throw new BusinessException(ErrorCode.BOOK_NOT_FOUND);
        }

        List<Object[]> heldQuantities = stockHeldRepository.sumHeldQuantityByBookIds(bookIds);
        Map<Long, Integer> heldMap = heldQuantities.stream()
                .collect(Collectors.toMap(
                        row -> (Long) row[0],
                        row -> ((Number) row[1]).intValue() // DB에 따라 Long/BigDecimal일 수 있음
                ));

        List<StockHeld> newHolds = new ArrayList<>();

        // 2. 재고 검증 및 선점 엔티티 생성
        for (StockRequest req : requests) {
            Book book = bookMap.get(req.getBookId());
            int currentHeld = heldMap.getOrDefault(req.getBookId(), 0);
            int availableStock = book.getStock() - currentHeld;

            // 재고 부족 시 예외 발생 -> @Transactional에 의해 전체 롤백됨 (All or Nothing)
            if (availableStock < req.getQuantity()) {
                log.warn("[재고 일괄 선점 실패: 재고 부족] BookId={}, Available={}, Requested={}",
                        book.getId(), availableStock, req.getQuantity());
                throw new BusinessException(ErrorCode.OUT_OF_STOCK);
            }

            // 동일 요청 내 중복 도서 처리를 위해 선점 수량 갱신
            heldMap.put(req.getBookId(), currentHeld + req.getQuantity());

            String idempotencyKey = orderKey + "-" + req.getBookId();
            newHolds.add(StockHeld.builder()
                    .book(book)
                    .quantity(req.getQuantity())
                    .orderKey(orderKey)
                    .idempotencyKey(idempotencyKey)
                    .build());
        }

        stockHeldRepository.saveAll(newHolds);
        log.info("[재고 일괄 선점 완료] OrderKey={}, ItemCount={}", orderKey, requests.size());
    }

    @Override
    @Transactional
    public void releaseHeldStock(List<Long> bookIds, String orderKey) {
        // TCC Cancel 단계: PENDING 주문 취소 시 (Try 단계 보상)
        List<StockHeld> heldStocks = stockHeldRepository.findAllByOrderKeyAndBook_IdIn(orderKey, bookIds);

        if (heldStocks.isEmpty()) {
            return;
        }

        stockHeldRepository.deleteAll(heldStocks);
        log.info("[재고 선점 해제] OrderKey={}, ReleasedItemCount={}", orderKey, heldStocks.size());
    }

    @Override
    @Transactional
    public void confirmStockDeduction(String orderKey, List<Long> bookIds) {
        // 멱등성 검사 (이미 처리된 주문인지)
        String idempotencyKey = "CONFIRM-" + orderKey; // Confirm용 키 생성
        if (idempotencyRepository.existsByIdempotencyKey(idempotencyKey)) {
            log.info("[멱등 처리] 이미 재고 차감 확정된 주문 OrderKey={}", orderKey);
            return;
        }

        List<StockHeld> heldStocks = stockHeldRepository.findAllByOrderKeyAndBook_IdIn(orderKey, bookIds);
        if (heldStocks.isEmpty()) {
            log.warn("[재고 차감 실패] 선점 내역 없음 OrderKey={}", orderKey);
            return;
        } else {
            for (StockHeld held : heldStocks) {
                Book book = held.getBook();
                int quantity = held.getQuantity();

                book.setStock(book.getStock() - held.getQuantity());
                book.setSalesVolume(book.getSalesVolume() + quantity);

                stockHeldRepository.delete(held); // 선점 삭제
            }
            Set<Book> books = heldStocks.stream()
                    .map(StockHeld::getBook)
                    .collect(Collectors.toSet());
            bookRepository.saveAll(books);
        }

        // 2. 처리 기록 저장
        saveIdempotencyRecord(idempotencyKey, "CONFIRM");
        log.info("[재고 차감 확정] OrderKey={}, BookCount={}", orderKey, heldStocks.size());
    }



    @Override
    @Transactional
    public void restoreStock(List<StockRequest> requests, String idempotencyKey) {
        // 주문 취소/반품 시 재고 복구
        if (idempotencyRepository.existsByIdempotencyKey(idempotencyKey)) {
            log.warn("[멱등 처리] 이미 재고 복구 처리됨 Key={}", idempotencyKey);
            return;
        }

        if (requests == null || requests.isEmpty()) return;

        for (StockRequest request : requests) {
            Book book = getBookOrThrow(request.getBookId());

            int quantity = request.getQuantity();
            int restoredStock = book.getStock() + quantity;

            book.setStock(restoredStock);

            // 판매량 차감 (최소 0 유지)
            long newSalesVolume = Math.max(0, book.getSalesVolume() - quantity);
            book.setSalesVolume(newSalesVolume);

            log.info("[재고 복구 완료] BookId={}, Quantity={}, CurrentStock={}, SalesVolume={}",
                    book.getId(), quantity, restoredStock, newSalesVolume);
        }

        saveIdempotencyRecord(idempotencyKey, "RESTORE");
    }

    private void saveIdempotencyRecord(String key, String type) {
        idempotencyRepository.save(StockIdempotencyRecord.builder()
                .idempotencyKey(key)
                .status("SUCCESS")
                .type(type)
                .createdAt(LocalDateTime.now())
                .build());
    }
}