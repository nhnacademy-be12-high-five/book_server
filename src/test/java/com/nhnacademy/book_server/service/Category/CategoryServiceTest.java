package com.nhnacademy.book_server.service.Category;

import com.nhnacademy.book_server.dto.BookResponse;
import com.nhnacademy.book_server.dto.CategoryResponse;
import com.nhnacademy.book_server.entity.Category;
import com.nhnacademy.book_server.repository.CategoryRepository;
import com.nhnacademy.book_server.service.BookService;
import com.nhnacademy.book_server.service.category.CategoryService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class CategoryServiceTest {

    @InjectMocks
    private CategoryService categoryService;

    @Mock
    private CategoryRepository categoryRepository;

    @Mock
    private BookService bookService;

    @Test
    @DisplayName("카테고리 생성 성공")
    void createCategory() {
        // given
        int id = 1;
        String name = "국내도서";
        int parentId = 0;
        int depth = 1;

        // when
        categoryService.createCategory(id, name, parentId, depth);

        // then
        // save가 호출되었는지, 그리고 전달된 객체의 값이 맞는지 검증
        ArgumentCaptor<Category> captor = ArgumentCaptor.forClass(Category.class);
        verify(categoryRepository, times(1)).save(captor.capture());

        Category savedCategory = captor.getValue();
        assertThat(savedCategory.getCategoryId()).isEqualTo(id);
        assertThat(savedCategory.getCategoryName()).isEqualTo(name);
        assertThat(savedCategory.getParentId()).isEqualTo(parentId);
        assertThat(savedCategory.getDepth()).isEqualTo(depth);
    }

    @Test
    @DisplayName("대분류(Root) 카테고리 조회")
    void getParents() {
        // given
        Category rootCategory = new Category(1, "국내도서", 0, 1);
        given(categoryRepository.findByDepth(1)).willReturn(List.of(rootCategory));

        // when
        List<CategoryResponse> result = categoryService.getParents();

        // then
        assertThat(result).hasSize(1);

        verify(categoryRepository).findByDepth(1);
    }

    @Test
    @DisplayName("하위 카테고리 조회")
    void getChilds() {
        // given
        int parentId = 1;
        Category childCategory = new Category(10, "소설", parentId, 2);
        given(categoryRepository.findByParentId(parentId)).willReturn(List.of(childCategory));

        // when
        List<CategoryResponse> result = categoryService.getChilds(parentId);

        // then
        assertThat(result).hasSize(1);

        verify(categoryRepository).findByParentId(parentId);
    }

    @Test
    @DisplayName("카테고리별 도서 조회 - 성공")
    void getBooksByCategory_Success() {
        // given
        int categoryId = 10;
        Pageable pageable = PageRequest.of(0, 10);
        Category category = new Category(categoryId, "소설", 1, 2);

        // 1. 카테고리 존재 확인 Mocking
        given(categoryRepository.findByCategoryId(categoryId)).willReturn(Optional.of(category));

        // 2. BookService 호출 Mocking
        Page<BookResponse> emptyPage = new PageImpl<>(Collections.emptyList());
        given(bookService.getBooksByCategory(categoryId, pageable)).willReturn(emptyPage);

        // when
        Page<BookResponse> result = categoryService.getBooksByCategory(categoryId, pageable);

        // then
        assertThat(result).isNotNull();
        assertThat(result.getContent()).isEmpty();

        // 메서드 호출 검증
        verify(categoryRepository).findByCategoryId(categoryId);
        verify(bookService).getBooksByCategory(categoryId, pageable);
    }

    @Test
    @DisplayName("카테고리별 도서 조회 - 실패 (카테고리 없음)")
    void getBooksByCategory_Fail_NotFound() {
        // given
        int categoryId = 999;
        Pageable pageable = PageRequest.of(0, 10);

        // 카테고리가 없어서 Optional.empty() 반환
        given(categoryRepository.findByCategoryId(categoryId)).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> categoryService.getBooksByCategory(categoryId, pageable))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("category not found");

        // BookService는 호출되지 않아야 함
        verify(bookService, times(0)).getBooksByCategory(any(Integer.class), any(Pageable.class));
    }
}