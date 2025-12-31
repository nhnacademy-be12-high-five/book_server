package com.nhnacademy.book_server.service.category;

import com.nhnacademy.book_server.dto.BookResponse;
import com.nhnacademy.book_server.dto.CategoryResponse;
import com.nhnacademy.book_server.entity.Category;
import com.nhnacademy.book_server.repository.CategoryRepository;
import com.nhnacademy.book_server.service.BookService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class CategoryService {
    private final CategoryRepository categoryRepository;
    private final BookService bookService;

    @Transactional
    public void createCategory(int id, String name, int parentId, int depth) {
        Category category = new Category(id, name, parentId, depth);
        categoryRepository.save(category);
    }

    // 대분류
    @Transactional(readOnly = true)
    public List<CategoryResponse> getParents() {
        return categoryRepository.findByDepth(1)
                .stream()
                .map(c -> new CategoryResponse(c.getCategoryId(), c.getCategoryName()))
                .toList();
    }

    // 하위 카테고리 조회
    @Transactional(readOnly = true)
    public List<CategoryResponse> getChilds(int parentId) {
        return categoryRepository.findByParentId(parentId)
                .stream()
                .map(c -> new CategoryResponse(c.getCategoryId(), c.getCategoryName()))
                .toList();
    }

    // 카테고리별 도서 조회
    @Transactional(readOnly = true)
    public Page<BookResponse> getBooksByCategory(int categoryId, Pageable pageable) {
        // category 존재 검증만 수행
        categoryRepository.findByCategoryId(categoryId)
                .orElseThrow(() -> new IllegalArgumentException("category not found: " + categoryId));

        return bookService.getBooksByCategory(categoryId, pageable);
    }
}
