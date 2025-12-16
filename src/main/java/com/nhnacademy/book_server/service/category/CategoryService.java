package com.nhnacademy.book_server.service.category;

import com.nhnacademy.book_server.dto.BookResponse;
import com.nhnacademy.book_server.dto.CategoryResponse;
import com.nhnacademy.book_server.entity.Book;
import com.nhnacademy.book_server.entity.BookCategory;
import com.nhnacademy.book_server.entity.Category;
import com.nhnacademy.book_server.repository.BookCategoryRepository;
import com.nhnacademy.book_server.repository.BookRepository;
import com.nhnacademy.book_server.repository.CategoryRepository;
import com.nhnacademy.book_server.service.BookService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class CategoryService {
    private final CategoryRepository categoryRepository;
    private final BookCategoryRepository bookCategoryRepository;
    private final BookRepository bookRepository;
    private final BookService bookService;

    // 대분류
    public List<CategoryResponse> getParents() {
        return categoryRepository.findByDepth(1)
                .stream()
                .map(c -> new CategoryResponse(c.getCategoryId(), c.getCategoryName()))
                .toList();
    }

    // 하위 카테고리 조회
    public List<CategoryResponse> getChilds(int parentId) {
        return categoryRepository.findByParentId(parentId)
                .stream()
                .map(c -> new CategoryResponse(c.getCategoryId(), c.getCategoryName()))
                .toList();
    }

    // 카테고리별 도서 조회
    // CategoryService
    public List<BookResponse> getBooksByCategory(int categoryId) {
        // category 존재 검증만 수행
        categoryRepository.findByCategoryId(categoryId)
                .orElseThrow(() -> new IllegalArgumentException("category not found: " + categoryId));

        return bookService.getBooksByCategory(categoryId);
    }




}
