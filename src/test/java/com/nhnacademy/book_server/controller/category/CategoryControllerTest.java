package com.nhnacademy.book_server.controller.category;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nhnacademy.book_server.controller.CategoryController;
import com.nhnacademy.book_server.dto.BookResponse;
import com.nhnacademy.book_server.dto.CategoryResponse;
import com.nhnacademy.book_server.entity.Category;
import com.nhnacademy.book_server.service.category.CategoryService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(CategoryController.class)
@WithMockUser(username = "admin", roles = {"ADMIN"})
class CategoryControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CategoryService categoryService;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @DisplayName("카테고리 생성 API 성공 테스트")
    void createCategory_Success() throws Exception {
        // given
        Category category = new Category(1, "IT", 0, 1);
        String content = objectMapper.writeValueAsString(category);

        // when & then
        mockMvc.perform(post("/api/categories")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(content))
                .andExpect(status().isOk())
                .andDo(print());

        // verify service call
        verify(categoryService).createCategory(category.getCategoryId(), category.getCategoryName(), category.getParentId(), category.getDepth());
    }

    @Test
    @DisplayName("상위(대분류) 카테고리 조회 API 테스트")
    void getParents_Success() throws Exception {
        // given
        List<CategoryResponse> responses = List.of(
                new CategoryResponse(1, "국내도서"),
                new CategoryResponse(2, "외국도서")
        );

        given(categoryService.getParents()).willReturn(responses);

        // when & then
        mockMvc.perform(get("/api/categories/parent")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.size()").value(2))
                .andExpect(jsonPath("$[0].categoryId").value(1))
                .andExpect(jsonPath("$[0].categoryName").value("국내도서"))
                .andExpect(jsonPath("$[1].categoryId").value(2))
                .andDo(print());
    }

    @Test
    @DisplayName("하위 카테고리 조회 API 테스트")
    void getChilds_Success() throws Exception {
        // given
        int parentId = 1;
        List<CategoryResponse> responses = List.of(
                new CategoryResponse(10, "소설"),
                new CategoryResponse(11, "IT/컴퓨터")
        );

        given(categoryService.getChilds(parentId)).willReturn(responses);

        // when & then
        mockMvc.perform(get("/api/categories/{parentId}/child", parentId)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.size()").value(2))
                .andExpect(jsonPath("$[0].categoryId").value(10))
                .andExpect(jsonPath("$[0].categoryName").value("소설"))
                .andDo(print());
    }

    @Test
    @DisplayName("카테고리별 도서 조회 API 테스트 (페이징 포함)")
    void getBooksByCategory_Success() throws Exception {
        // given
        int categoryId = 10;
        int page = 0;
        int size = 12;

        // Mock 데이터 생성
        BookResponse book1 = createMockBookResponse(100L, "자바의 정석");
        BookResponse book2 = createMockBookResponse(101L, "JPA 프로그래밍");

        List<BookResponse> bookList = List.of(book1, book2);
        Page<BookResponse> bookPage = new PageImpl<>(bookList, PageRequest.of(page, size), bookList.size());

        // Service Mocking
        given(categoryService.getBooksByCategory(eq(categoryId), any(Pageable.class)))
                .willReturn(bookPage);

        // when & then
        mockMvc.perform(get("/api/categories/{categoryId}/books", categoryId)
                        .param("page", String.valueOf(page))
                        .param("size", String.valueOf(size))
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                // Page 객체 응답 구조 검증 (content 내부에 데이터 존재)
                .andExpect(jsonPath("$.content.size()").value(2))
                .andExpect(jsonPath("$.content[0].id").value(100))
                .andExpect(jsonPath("$.content[0].title").value("자바의 정석"))
                .andExpect(jsonPath("$.content[1].title").value("JPA 프로그래밍"))
                .andExpect(jsonPath("$.totalElements").value(2))
                .andDo(print());
    }

    // 테스트용 BookResponse 생성 헬퍼 메서드
    private BookResponse createMockBookResponse(Long id, String title) {
        // BookResponse 생성자가 많거나 복잡할 경우 Builder 패턴이나 적절한 생성자 사용
        // 여기서는 예시로 필요한 필드만 채우거나 null 처리
        return new BookResponse(
                id, title, "Author", "ISBN", 10000, "image.jpg",
                null, null, "Desc", "Pub", "2024-01-01",
                4.5, 10L, "Summary", null, null, null
        );
    }
}