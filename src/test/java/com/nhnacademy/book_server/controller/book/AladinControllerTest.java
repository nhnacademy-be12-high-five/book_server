
package com.nhnacademy.book_server.controller.book;

import com.nhnacademy.book_server.controller.AladinController;
import com.nhnacademy.book_server.entity.AladinItem;
import com.nhnacademy.book_server.service.impl.AladinServiceImpl;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AladinController.class)
@WithMockUser(username = "admin", roles = {"ADMIN"})
class AladinControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AladinServiceImpl aladinService;

    @Test
    @DisplayName("도서 검색 API 테스트")
    void search() throws Exception {
        // given
        AladinItem item = new AladinItem();
        item.setTitle("자바의 정석");
        item.setIsbn13("1234567890123");

        given(aladinService.searchBooks(anyString(), anyString()))
                .willReturn(List.of(item));

        // when & then
        mockMvc.perform(get("/api/aladin/search")
                        .param("query", "자바")
                        .param("queryType", "Title")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].title").value("자바의 정석"))
                .andExpect(jsonPath("$[0].isbn13").value("1234567890123"));
    }

    @Test
    @DisplayName("도서 상세 조회 API 테스트")
    void lookup() throws Exception {
        // given
        AladinItem item = new AladinItem();
        item.setTitle("상세 조회 책");
        item.setIsbn13("9791163035105");

        given(aladinService.lookupBook(anyString()))
                .willReturn(item);

        // when & then
        mockMvc.perform(get("/api/aladin/lookup")
                        .param("isbn13", "9791163035105")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("상세 조회 책"))
                .andExpect(jsonPath("$.isbn13").value("9791163035105"));
    }

    @Test
    @DisplayName("베스트셀러/신간 리스트 조회 API 테스트")
    void getList() throws Exception {
        // given
        AladinItem item = new AladinItem();
        item.setTitle("베스트셀러 책");

        given(aladinService.getBookList(anyString()))
                .willReturn(List.of(item));

        // when & then
        mockMvc.perform(get("/api/aladin/list")
                        .param("queryType", "Bestseller")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].title").value("베스트셀러 책"));
    }
}