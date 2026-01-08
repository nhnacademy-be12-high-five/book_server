package com.nhnacademy.book_server.service.Category;


import com.nhnacademy.book_server.entity.Book;
import com.nhnacademy.book_server.entity.BookCategory;
import com.nhnacademy.book_server.entity.Category;
import com.nhnacademy.book_server.repository.BookCategoryRepository;
import com.nhnacademy.book_server.repository.CategoryRepository;
import com.nhnacademy.book_server.service.category.CategoryMappingService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CategoryMappingServiceTest {

    @InjectMocks
    private CategoryMappingService categoryMappingService;

    @Mock
    private CategoryRepository categoryRepository;

    @Mock
    private BookCategoryRepository bookCategoryRepository;

    @Test
    @DisplayName("새로운 소분류(8번) 매핑 시: 카테고리 생성 -> 본인 매핑 -> 부모(1번) 매핑까지 수행되어야 함")
    void categoryAndMap_Success_WithParent() {
        // Given
        Long bookId = 100L;
        Book book = new Book();
        book.setId(bookId);

        int childCategoryId = 8; // 소설 (CategoryMapper 로직상 부모는 1)
        String categoryName = "소설";

        // 1. 소분류 카테고리가 DB에 없다고 가정 (새로 생성 로직 검증)
        given(categoryRepository.findByCategoryId(childCategoryId))
                .willReturn(Optional.empty());

        // 2. 새로 생성될 카테고리 객체 Mocking (save 호출 시 반환될 객체)
        Category savedChildCategory = new Category(childCategoryId, categoryName, 1, 2);
        given(categoryRepository.save(any(Category.class)))
                .willReturn(savedChildCategory);

        // 3. 부모 카테고리(1번)는 DB에 이미 있다고 가정
        Category parentCategory = new Category(1, "문학", 0, 1);
        given(categoryRepository.findByCategoryId(1))
                .willReturn(Optional.of(parentCategory));

        // 4. 아직 책-카테고리 매핑은 되어있지 않음 (existsById -> false)
        given(bookCategoryRepository.existsById(any(BookCategory.Pk.class)))
                .willReturn(false);

        // When
        categoryMappingService.categoryAndMap(book, childCategoryId, categoryName);

        // Then
        // 1. 카테고리가 없었으니 save(newCategory)가 1번 호출되어야 함
        verify(categoryRepository, times(1)).save(any(Category.class));

        // 2. 매핑(BookCategory) 저장은 총 2번 일어나야 함 (소분류 1번 + 대분류 1번)
        verify(bookCategoryRepository, times(2)).save(any(BookCategory.class));
    }

    @Test
    @DisplayName("이미 매핑된 정보가 있다면 저장을 건너뛰어야 한다")
    void categoryAndMap_Skip_If_Exists() {
        // Given
        Book book = new Book();
        book.setId(1L);
        int categoryId = 1; // 대분류 (부모 없음)
        String name = "문학";

        Category category = new Category(categoryId, name, 0, 1);

        // 카테고리는 이미 존재함
        given(categoryRepository.findByCategoryId(categoryId))
                .willReturn(Optional.of(category));

        // 매핑도 이미 존재함 (existsById -> true)
        given(bookCategoryRepository.existsById(any(BookCategory.Pk.class)))
                .willReturn(true);

        // When
        categoryMappingService.categoryAndMap(book, categoryId, name);

        // Then
        // 매핑이 이미 존재하므로 save는 호출되지 않아야 함
        verify(bookCategoryRepository, never()).save(any(BookCategory.class));
    }
}