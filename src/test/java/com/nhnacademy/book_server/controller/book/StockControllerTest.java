package com.nhnacademy.book_server.controller.book;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nhnacademy.book_server.controller.StockController;
import com.nhnacademy.book_server.dto.request.StockRequest;
import com.nhnacademy.book_server.service.StockService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.times;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(StockController.class)
@WithMockUser(username = "user", roles = "USER") // 기본 인증 유저 설정
class StockControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private StockService stockService;

    // 테스트용 헤더 및 키 상수
    private static final String IDEMPOTENCY_KEY_HEADER = "Idempotency-Key";
    private static final String TEST_IDEMPOTENCY_KEY = "test-uuid-1234";
    private static final String TEST_ORDER_KEY = "order-5678";

    @Test
    @DisplayName("1. TCC Try: 단건 재고 선점 성공")
    void holdStock() throws Exception {
        // given
        Long bookId = 1L;
        int quantity = 5;

        // when & then
        mockMvc.perform(post("/api/books/{bookId}/stock/hold", bookId)
                        .with(csrf())
                        .param("quantity", String.valueOf(quantity))
                        .header(IDEMPOTENCY_KEY_HEADER, TEST_IDEMPOTENCY_KEY))
                .andDo(print())
                .andExpect(status().isOk());

        // verify: 서비스가 올바른 파라미터로 호출되었는지 검증
        then(stockService).should(times(1))
                .holdStock(eq(bookId), eq(quantity), eq(TEST_IDEMPOTENCY_KEY));
    }

    @Test
    @DisplayName("1-1. TCC Try (Batch): 재고 일괄 선점 성공")
    void holdStockBatch() throws Exception {
        // given
        // StockRequest 생성 (생성자나 빌더가 있다고 가정, 테스트를 위해 가짜 데이터 생성)
        StockRequest req1 = new StockRequest(1L, 2);
        StockRequest req2 = new StockRequest(2L, 3);
        List<StockRequest> requests = List.of(req1, req2);

        String jsonBody = objectMapper.writeValueAsString(requests);

        // when & then
        mockMvc.perform(post("/api/books/stock/hold/batch")
                        .with(csrf())
                        .param("orderKey", TEST_ORDER_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonBody))
                .andDo(print())
                .andExpect(status().isOk());

        then(stockService).should(times(1))
                .holdStockBatch(anyList(), eq(TEST_ORDER_KEY));
    }

    @Test
    @DisplayName("2. TCC Cancel: 재고 선점 해제 성공")
    void releaseHeldStock() throws Exception {
        // given
        List<Long> bookIds = List.of(1L, 2L, 3L);
        String jsonBody = objectMapper.writeValueAsString(bookIds);

        // when & then
        mockMvc.perform(post("/api/books/release-stock")
                        .with(csrf())
                        .param("orderKey", TEST_ORDER_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonBody))
                .andDo(print())
                .andExpect(status().isOk());

        then(stockService).should(times(1))
                .releaseHeldStock(anyList(), eq(TEST_ORDER_KEY));
    }

    @Test
    @DisplayName("3. TCC Confirm: 재고 확정 차감 성공")
    void confirmStockDeduction() throws Exception {
        // given
        List<Long> bookIds = List.of(10L, 20L);
        String jsonBody = objectMapper.writeValueAsString(bookIds);

        // when & then
        mockMvc.perform(post("/api/books/stock/confirm-deduction")
                        .with(csrf())
                        .param("orderKey", TEST_ORDER_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonBody))
                .andDo(print())
                .andExpect(status().isOk());

        // verify: Controller 내부에서 Service 호출 시 인자 순서가 (orderKey, bookIds)로 바뀌는지 확인
        then(stockService).should(times(1))
                .confirmStockDeduction(eq(TEST_ORDER_KEY), anyList());
    }

    @Test
    @DisplayName("4. TCC Cancel: 재고 복구 (WAITING 취소) 성공")
    void restoreStock() throws Exception {
        // given
        StockRequest req1 = new StockRequest(5L, 1);
        List<StockRequest> requests = List.of(req1);
        String jsonBody = objectMapper.writeValueAsString(requests);

        // when & then
        mockMvc.perform(post("/api/books/stock/restore")
                        .with(csrf())
                        .header(IDEMPOTENCY_KEY_HEADER, TEST_IDEMPOTENCY_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonBody))
                .andDo(print())
                .andExpect(status().isOk());

        then(stockService).should(times(1))
                .restoreStock(anyList(), eq(TEST_IDEMPOTENCY_KEY));
    }
}