package com.nhnacademy.book_server.controller;

import com.nhnacademy.book_server.dto.response.SearchLogResponse;
import com.nhnacademy.book_server.service.search.SearchLogService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(SearchLogController.class)
@AutoConfigureMockMvc(addFilters = false)
class SearchLogControllerTest {

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    SearchLogService searchLogService;

    @Test
    @DisplayName("GET /popular - limit 미전달 시 default=10으로 서비스 호출 + 200 OK")
    void getPopularKeywords_defaultLimit_10() throws Exception {
        List<SearchLogResponse> body = List.of(
                new SearchLogResponse("java", 100L),
                new SearchLogResponse("spring", 80L)
        );
        when(searchLogService.getPopularKeywords(10)).thenReturn(body);

        mockMvc.perform(get("/popular"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].keyword").value("java"))
                .andExpect(jsonPath("$[0].searchCount").value(100))
                .andExpect(jsonPath("$[1].keyword").value("spring"))
                .andExpect(jsonPath("$[1].searchCount").value(80));

        verify(searchLogService).getPopularKeywords(10);
    }

    @Test
    @DisplayName("GET /popular?limit=3 - 전달값 그대로 서비스 호출 + 200 OK")
    void getPopularKeywords_customLimit_3() throws Exception {
        List<SearchLogResponse> body = List.of(
                new SearchLogResponse("k1", 3L),
                new SearchLogResponse("k2", 2L),
                new SearchLogResponse("k3", 1L)
        );
        when(searchLogService.getPopularKeywords(3)).thenReturn(body);

        mockMvc.perform(get("/popular").param("limit", "3"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[2].keyword").value("k3"))
                .andExpect(jsonPath("$[2].searchCount").value(1));

        verify(searchLogService).getPopularKeywords(3);
    }

}
