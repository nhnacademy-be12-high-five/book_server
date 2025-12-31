//package com.nhnacademy.book_server.controller.category;
//
//import com.fasterxml.jackson.databind.ObjectMapper;
//import com.nhnacademy.book_server.controller.CategoryController;
//import com.nhnacademy.book_server.dto.BookResponse;
//import com.nhnacademy.book_server.dto.CategoryResponse;
//import com.nhnacademy.book_server.entity.Category;
//import com.nhnacademy.book_server.service.category.CategoryService;
//import org.junit.jupiter.api.DisplayName;
//import org.junit.jupiter.api.Test;
//import org.mockito.Mock;
//import org.springframework.beans.factory.annotation.Autowired;
//import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
//import org.springframework.http.MediaType;
//import org.springframework.security.test.context.support.WithMockUser;
//import org.springframework.test.web.servlet.MockMvc;
//
//import java.util.Collections;
//import java.util.List;
//
//import static org.mockito.BDDMockito.given;
//import static org.mockito.Mockito.verify;
//import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
//import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
//import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
//import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
//import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
//
//import static org.mockito.ArgumentMatchers.any;
//import static org.mockito.ArgumentMatchers.eq;
//
//@WebMvcTest(CategoryController.class)
//@WithMockUser // 401 Unauthorized 방지
//class CategoryControllerTest {
//
//    @Autowired
//    private MockMvc mockMvc;
//
//    private CategoryService categoryService;
//
//    @Autowired
//    private ObjectMapper objectMapper;
//
//    @Test
//    @DisplayName("카테고리 생성 테스트")
//    void createCategory() throws Exception {
//        // given
//        // Category 엔티티에 적절한 생성자나 Setter가 있다고 가정
//        // Service 코드에서 new Category(id, name, parentId, depth)를 쓰는 걸로 보아 생성자가 존재함.
//        Category category = new Category(10, "국내도서", 0, 1);
//
//        // when & then
//        mockMvc.perform(post("/api/categories")
//                        .with(csrf()) // POST 요청 필수
//                        .content(objectMapper.writeValueAsString(category))
//                        .contentType(MediaType.APPLICATION_JSON))
//                .andExpect(status().isOk());
//
//        // verify
//        verify(categoryService).createCategory(10, "국내도서", 0, 1);
//    }
//
//    @Test
//    @DisplayName("대분류(부모) 카테고리 조회 테스트")
//    void getParents() throws Exception {
//        // given
//        List<CategoryResponse> responses = List.of(
//                new CategoryResponse(1, "국내도서"),
//                new CategoryResponse(2, "외국도서")
//        );
//
//        given(categoryService.getParents()).willReturn(responses);
//
//        // when & then
//        mockMvc.perform(get("/api/categories/parent")
//                        .contentType(MediaType.APPLICATION_JSON))
//                .andExpect(status().isOk())
//                .andExpect(jsonPath("$.size()").value(2))
//                .andExpect(jsonPath("$[0].categoryName").value("국내도서"));
//    }
//
//    @Test
//    @DisplayName("하위(자식) 카테고리 조회 테스트")
//    void getChilds() throws Exception {
//        // given
//        int parentId = 1;
//        List<CategoryResponse> responses = List.of(
//                new CategoryResponse(11, "소설"),
//                new CategoryResponse(12, "수필")
//        );
//
//        given(categoryService.getChilds(parentId)).willReturn(responses);
//
//        // when & then
//        mockMvc.perform(get("/api/categories/{parentId}/child", parentId)
//                        .contentType(MediaType.APPLICATION_JSON))
//                .andExpect(status().isOk())
//                .andExpect(jsonPath("$.size()").value(2))
//                .andExpect(jsonPath("$[0].categoryName").value("소설"));
//    }
//
//    @Test
//    @DisplayName("카테고리별 도서 조회 테스트")
//    void getBooksByCategory() throws Exception {
//        // given
//        int categoryId = 10;
//
//        // BookResponse 생성자 사용 (순서 중요)
//        BookResponse bookResponse = new BookResponse(
//                1L,                     // id
//                "테스트 도서",             // title
//                "테스트 작가",             // author
//                "1234567890123",        // isbn
//                15000,                  // price
//                "http://image.url",     // image
//                Collections.emptyList(), // categories
//                Collections.emptyList(), // tags
//                "내용입니다",              // content
//                "테스트 출판사",            // publisher
//                "2024-12-25",           // publishedDate
//                4.8,                    // avgRating
//                100L,                   // reviewCount
//                null,                   // aiSummary
//                null                    // aiReviewSummary
//        );
//
//        given(categoryService.getBooksByCategory(categoryId))
//                .willReturn(List.of(bookResponse));
//
//        // when & then
//        mockMvc.perform(get("/api/categories/{categoryId}/books", categoryId)
//                        .contentType(MediaType.APPLICATION_JSON))
//                .andExpect(status().isOk())
//                .andExpect(jsonPath("$.size()").value(1))
//                .andExpect(jsonPath("$[0].title").value("테스트 도서"));
//    }
//}