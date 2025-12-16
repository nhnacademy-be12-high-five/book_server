package com.nhnacademy.book_server.service.impl;

import com.nhnacademy.book_server.dto.request.StockRequest;
import com.nhnacademy.book_server.entity.Book;
import com.nhnacademy.book_server.entity.StockHeld;
import com.nhnacademy.book_server.exception.BusinessException;
import com.nhnacademy.book_server.exception.ErrorCode;
import com.nhnacademy.book_server.repository.BookRepository;
import com.nhnacademy.book_server.repository.StockHeldRepository;
import com.nhnacademy.book_server.service.StockService;
import java.util.ArrayList;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
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
        Integer currentHeldQuantity = stockHeldRepository.sumHeldQuantityByBookId(bookId);
        int availableStock = book.getStock() - currentHeldQuantity;

        if (availableStock < quantity) {
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
    public void confirmStockDeduction(String orderKey, List<Long> bookIds) {
        // TCC Confirm 단계: 결제 성공 시 확정 차감

        List<StockHeld> heldStocks = stockHeldRepository.findAllByOrderKeyAndBook_IdIn(orderKey, bookIds);

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
    public void holdStockBatch(List<StockRequest> requests, String orderKey) {
        if (requests == null || requests.isEmpty()) return;

        // 1. 요청된 책들의 ID 추출
        List<Long> bookIds = requests.stream()
                .map(StockRequest::getBookId)
                .toList();

        // 2. 책 정보 일괄 조회 (Locking을 고려한다면 PESSIMISTIC_WRITE 락 사용 가능, 여기선 생략)
        List<Book> books = bookRepository.findAllById(bookIds);
        Map<Long, Book> bookMap = books.stream()
                .collect(Collectors.toMap(Book::getId, Function.identity()));

        // 모든 책이 존재하는지 확인
        if (books.size() != bookIds.size()) {
            throw new BusinessException(ErrorCode.BOOK_NOT_FOUND);
        }

        // 3. 현재 선점된 수량 일괄 조회 (DB 부하 감소)
        List<Object[]> heldQuantities = stockHeldRepository.sumHeldQuantityByBookIds(bookIds);
        Map<Long, Integer> heldMap = heldQuantities.stream()
                .collect(Collectors.toMap(
                        row -> (Long) row[0],
                        row -> ((Number) row[1]).intValue() // DB에 따라 Long/BigDecimal일 수 있음
                ));

        List<StockHeld> newHolds = new ArrayList<>();

        // 4. 각 요청에 대해 가용 재고 검증
        for (StockRequest req : requests) {
            Book book = bookMap.get(req.getBookId());
            int currentHeld = heldMap.getOrDefault(req.getBookId(), 0);
            int availableStock = book.getStock() - currentHeld;

            // 재고 부족 시 예외 발생 -> @Transactional에 의해 전체 롤백됨 (All or Nothing)
            if (availableStock < req.getQuantity()) {
                log.warn("Batch Stock hold failed: BookId={}, Available={}, Requested={}",
                        book.getId(), availableStock, req.getQuantity());
                throw new BusinessException(ErrorCode.OUT_OF_STOCK);
            }

            // 현재 요청 내 중복 책 ID가 있을 경우를 대비해 heldMap 업데이트 (선택 사항)
            heldMap.put(req.getBookId(), currentHeld + req.getQuantity());

            // 저장할 엔티티 생성
            String idempotencyKey = orderKey + "-" + req.getBookId(); // 개별 키 생성

            newHolds.add(StockHeld.builder()
                    .book(book)
                    .quantity(req.getQuantity())
                    .orderKey(orderKey)
                    .idempotencyKey(idempotencyKey)
                    .build());
        }

        // 5. 일괄 저장
        stockHeldRepository.saveAll(newHolds);
        log.info("Batch Stock hold success: OrderKey={}, Items={}", orderKey, requests.size());
    }

    @Override
    @Transactional
    public void restoreStock(List<StockRequest> requests, String idempotencyKey) {
        // TCC Cancel 단계: WAITING 주문 취소 (환불) 시 재고 복구

        // WAITING 취소(환불)는 실제 재고를 복구해야 하므로, 수량 정보가 필수입니다.
        // 현재 API는 bookIds만 받으므로, Order Server가 수량 정보를 보내도록 API를 변경해야 합니다.

        if (requests == null || requests.isEmpty()) {
            log.warn("Stock restore skipped: No items to restore for key={}", idempotencyKey);
            return;
        }

        // 1. 요청받은 목록 순회
        for (StockRequest request : requests) {
            // 2. 책 조회
            Book book = bookRepository.findById(request.getBookId())
                    .orElseThrow(() -> new BusinessException(ErrorCode.BOOK_NOT_FOUND));

            // 3. 재고 원복 (기존 재고 + 취소된 수량)
            // WAITING 취소는 이미 결제가 완료되어 재고가 차감된 상태에서 발생하므로,
            // 다시 수량을 더해주어야 합니다.
            int restoredStock = book.getStock() + request.getQuantity();
            book.setStock(restoredStock);

            log.info("Stock restored: BookId={}, RestoredQty={}, CurrentStock={}",
                    book.getId(), request.getQuantity(), restoredStock);
        }
    }
}