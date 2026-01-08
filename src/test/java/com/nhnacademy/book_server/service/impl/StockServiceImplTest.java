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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class StockServiceImplTest {

    @InjectMocks
    private StockServiceImpl stockService;

    @Mock
    private BookRepository bookRepository;

    @Mock
    private StockHeldRepository stockHeldRepository;

    @Mock
    private StockIdempotencyRepository idempotencyRepository;

    // --- holdStock (단건 선점) 테스트 ---

    @Test
    @DisplayName("holdStock: 이미 처리된 요청(멱등성)이면 로직 실행 없이 종료")
    void holdStock_Idempotent() {
        // Given
        String key = "order-123-item-1";

        // [수정] new StockHeld() -> StockHeld.builder().build() 또는 mock() 사용
        StockHeld emptyHeld = StockHeld.builder().build();

        given(stockHeldRepository.findByIdempotencyKey(key))
                .willReturn(Optional.of(emptyHeld));

        // When
        stockService.holdStock(1L, 5, key);

        // Then
        verify(bookRepository, never()).findById(any());
        verify(stockHeldRepository, never()).save(any());
    }

    @Test
    @DisplayName("holdStock: 성공 - 재고 충분 시 StockHeld 저장")
    void holdStock_Success() {
        // Given
        Long bookId = 1L;
        int reqQty = 2;
        String key = "ORDER_KEY-1"; // "UUID-BOOKID" 형식 가정
        
        Book book = new Book();
        book.setId(bookId);
        book.setStock(10); // 전체 재고 10

        given(stockHeldRepository.findByIdempotencyKey(key)).willReturn(Optional.empty());
        given(bookRepository.findById(bookId)).willReturn(Optional.of(book));
        
        // 현재 선점된 수량: 3 (남은 재고: 10 - 3 = 7 >= 2 성공)
        given(stockHeldRepository.sumHeldQuantityByBookId(bookId)).willReturn(3);

        // When
        stockService.holdStock(bookId, reqQty, key);

        // Then
        verify(stockHeldRepository).save(any(StockHeld.class));
    }

    @Test
    @DisplayName("holdStock: 실패 - 가용 재고 부족 (OutOfStock)")
    void holdStock_Fail_OutOfStock() {
        // Given
        Long bookId = 1L;
        int reqQty = 5;
        String key = "key";

        Book book = new Book();
        book.setStock(10); 

        given(stockHeldRepository.findByIdempotencyKey(key)).willReturn(Optional.empty());
        given(bookRepository.findById(bookId)).willReturn(Optional.of(book));
        
        // 이미 8개가 선점됨 -> 가용: 2 < 요청: 5
        given(stockHeldRepository.sumHeldQuantityByBookId(bookId)).willReturn(8);

        // When & Then
        assertThatThrownBy(() -> stockService.holdStock(bookId, reqQty, key))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.OUT_OF_STOCK);
    }

    // --- holdStockBatch (다건 선점) 테스트 ---

    @Test
    @DisplayName("holdStockBatch: 성공 - 모든 도서 재고 충분")
    void holdStockBatch_Success() {
        // Given
        String orderKey = "ORDER-UUID";
        StockRequest req1 = new StockRequest(1L, 2);
        StockRequest req2 = new StockRequest(2L, 1);
        List<StockRequest> requests = List.of(req1, req2);

        Book book1 = Book.builder().id(1L).stock(10).build();
        Book book2 = Book.builder().id(2L).stock(5).build();

        given(bookRepository.findAllById(anyList())).willReturn(List.of(book1, book2));
        
        // 이미 선점된 수량 Mocking (Object[] 형태: [bookId, sumQuantity])
        // book1은 1개 선점됨, book2는 선점 없음
        List<Object[]> heldStats = List.of(new Object[]{1L, 1}, new Object[]{2L, 0});
        given(stockHeldRepository.sumHeldQuantityByBookIds(anyList())).willReturn(heldStats);

        // When
        stockService.holdStockBatch(requests, orderKey);

        // Then
        verify(stockHeldRepository).saveAll(anyList()); // 일괄 저장 호출 확인
    }

    @Test
    @DisplayName("holdStockBatch: 실패 - 도서 정보 없음 (BookNotFound)")
    void holdStockBatch_Fail_BookNotFound() {
        // Given
        StockRequest req = new StockRequest(999L, 1);
        given(bookRepository.findAllById(anyList())).willReturn(Collections.emptyList());

        List<StockRequest> requestList = List.of(req);

        // When & Then
        assertThatThrownBy(() -> stockService.holdStockBatch(requestList, "key"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.BOOK_NOT_FOUND);
    }

    // --- releaseHeldStock (선점 해제) 테스트 ---

    @Test
    @DisplayName("releaseHeldStock: 선점된 재고 삭제 (TCC Cancel)")
    void releaseHeldStock_Success() {
        // Given
        String orderKey = "ORDER-123";
        List<Long> bookIds = List.of(1L, 2L);

        // [수정] new StockHeld() -> StockHeld.builder().build()
        StockHeld held = StockHeld.builder().orderKey(orderKey).build();

        given(stockHeldRepository.findAllByOrderKeyAndBook_IdIn(orderKey, bookIds))
                .willReturn(List.of(held));

        // When
        stockService.releaseHeldStock(bookIds, orderKey);

        // Then
        verify(stockHeldRepository).deleteAll(anyList());
    }

    // --- confirmStockDeduction (차감 확정) 테스트 ---

    @Test
    @DisplayName("confirmStockDeduction: 실제 재고 차감 및 선점 내역 삭제")
    void confirmStockDeduction_Success() {
        // Given
        String orderKey = "ORDER-123";
        List<Long> bookIds = List.of(1L);
        String confirmKey = "CONFIRM-" + orderKey;

        // 멱등성 체크: 처리된 적 없음
        given(idempotencyRepository.existsByIdempotencyKey(confirmKey)).willReturn(false);

        // 선점 내역 조회
        Book book = new Book();
        book.setId(1L);
        book.setStock(10);
        book.setSalesVolume(0L);

        StockHeld held = StockHeld.builder().book(book).quantity(2).build();
        given(stockHeldRepository.findAllByOrderKeyAndBook_IdIn(orderKey, bookIds))
                .willReturn(List.of(held));

        // When
        stockService.confirmStockDeduction(orderKey, bookIds);

        // Then
        // 1. 실제 재고 줄어들었는지 확인 (10 - 2 = 8)
        assertThat(book.getStock()).isEqualTo(8);
        // 2. 판매량 늘었는지 확인
        assertThat(book.getSalesVolume()).isEqualTo(2L);
        // 3. 선점 내역 개별 삭제 확인 (코드 상 delete 호출)
        verify(stockHeldRepository).delete(held);
        // 4. 변경된 도서 정보 저장 확인
        verify(bookRepository).saveAll(any());
        // 5. 멱등성 기록 저장 확인
        verify(idempotencyRepository).save(any(StockIdempotencyRecord.class));
    }

    @Test
    @DisplayName("confirmStockDeduction: 이미 처리된 주문이면 스킵")
    void confirmStockDeduction_Idempotent() {
        // Given
        String orderKey = "ORDER-123";
        String confirmKey = "CONFIRM-" + orderKey;
        given(idempotencyRepository.existsByIdempotencyKey(confirmKey)).willReturn(true);

        // When
        stockService.confirmStockDeduction(orderKey, List.of(1L));

        // Then
        verify(stockHeldRepository, never()).findAllByOrderKeyAndBook_IdIn(any(), any());
    }

    // --- restoreStock (재고 복구) 테스트 ---

    @Test
    @DisplayName("restoreStock: 재고 증가 및 판매량 감소 (반품/취소)")
    void restoreStock_Success() {
        // Given
        String key = "restore-key";
        Long bookId = 1L;
        int restoreQty = 3;
        StockRequest req = new StockRequest(bookId, restoreQty);

        given(idempotencyRepository.existsByIdempotencyKey(key)).willReturn(false);

        Book book = new Book();
        book.setId(bookId);
        book.setStock(5);
        book.setSalesVolume(10L);

        given(bookRepository.findById(bookId)).willReturn(Optional.of(book));

        // When
        stockService.restoreStock(List.of(req), key);

        // Then
        // 재고: 5 + 3 = 8
        assertThat(book.getStock()).isEqualTo(8);
        // 판매량: 10 - 3 = 7
        assertThat(book.getSalesVolume()).isEqualTo(7L);
        
        verify(idempotencyRepository).save(any(StockIdempotencyRecord.class));
    }
}